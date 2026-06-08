/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._
import chisel3.util.{switch, is}
import chisel3.util.MuxLookup

import fecundmare.util.LSUBundle
import fecundmare.util.enum.MemSize
import fecundmare.util.PreSiliconPerformanceCounter

object LSUState extends ChiselEnum {
  /*
   * LSU State
   *
   * 1. Idle State
   * 2. Request State
   * 3. Wait State
   * 4. Send State
   *
   */
  val sIdle, sRequest, sWait, sSend = Value
}

class LSU extends Module {
  val io = IO(new LSUBundle)

  // Maybe there is two FSM in the future,
  // just like SRAM module.
  val lsuState = RegInit(LSUState.sIdle)

  // Buffer Register
  //
  // Notice: `writeData` and `readData` looks
  // similar, but they are different. Processing takes
  // master of `writeData`, and LSU takes master
  // of `readData`.
  val address = RegInit(0.U(32.W))
  val length = RegInit(0.U(32.W))
  val writeData = RegInit(0.U(32.W))
  val readData = RegInit(0.U(32.W))
  val writeEnable = RegInit(false.B)
  val readEnable = RegInit(false.B)

  // State 1
  io.fromProcessing.ready := lsuState === LSUState.sIdle
  address := Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.address,
    address
  )
  length := Mux(io.fromProcessing.fire, io.fromProcessing.bits.length, length)
  writeData := Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.writeData,
    writeData
  )
  writeEnable := Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.writeEnable,
    writeEnable
  )
  readEnable := Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.readEnable,
    readEnable
  )

  // State 2
  val currentReadEnable =
    Mux(io.fromProcessing.fire, io.fromProcessing.bits.readEnable, readEnable)
  val currentWriteEnable =
    Mux(io.fromProcessing.fire, io.fromProcessing.bits.writeEnable, writeEnable)
  val currentAddress = Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.address,
    address
  )
  io.axi4.ar.valid := (lsuState === LSUState.sRequest || io.fromProcessing.fire) && currentReadEnable
  io.axi4.aw.valid := (lsuState === LSUState.sRequest || io.fromProcessing.fire) && currentWriteEnable
  io.axi4.w.valid := (lsuState === LSUState.sRequest || io.fromProcessing.fire) && currentWriteEnable
  io.axi4.w.bits.last := io.axi4.w.valid
  io.axi4.ar.bits.size := io.fromProcessing.bits.length
  io.axi4.ar.bits.addr := currentAddress
  io.axi4.aw.bits.size := io.fromProcessing.bits.length
  io.axi4.aw.bits.addr := currentAddress
  // It should align with the bus width in AXI4 transaction
  io.axi4.w.bits.strb := MuxLookup(io.fromProcessing.bits.length, "b1111".U)(
    Seq(
      MemSize.B.asUInt -> "b0001".U,
      MemSize.H.asUInt -> "b0011".U,
      MemSize.W.asUInt -> "b1111".U
    )
  ) << currentAddress(1, 0)
  io.axi4.w.bits.data := Mux(
    io.fromProcessing.fire,
    io.fromProcessing.bits.writeData,
    writeData
  ) << (currentAddress(1, 0) << 3)

  // Default value
  io.axi4.aw.bits.burst := 0.U
  io.axi4.aw.bits.id := 0.U
  io.axi4.aw.bits.len := 0.U
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.burst := 0.U

  // State 3
  io.axi4.r.ready := lsuState === LSUState.sWait || lsuState === LSUState.sSend // Skip the Wait state
  readData := Mux(io.axi4.r.fire, io.axi4.r.bits.data, readData)
  io.axi4.b.ready := lsuState === LSUState.sWait

  // State 4
  //
  // It should align with the bus width in AXI4 transaction
  io.toProcessing.valid := lsuState === LSUState.sSend || (lsuState === LSUState.sIdle && !currentWriteEnable && !currentReadEnable) || (lsuState === LSUState.sWait && io.axi4.r.fire)
  io.toProcessing.bits.readData := Mux(
    io.axi4.r.fire,
    io.axi4.r.bits.data,
    readData
  ) >> (currentAddress(1, 0) << 3)

  // Performance Counter
  PreSiliconPerformanceCounter("loadDataValidCounter", io.axi4.r.fire, 32)
  PreSiliconPerformanceCounter(
    "loadWaitingCycleCounter",
    io.axi4.r.ready && !io.axi4.r.fire,
    32
  )
  PreSiliconPerformanceCounter(
    "storeWaitingCycleCounter",
    io.axi4.b.ready && !io.axi4.b.fire,
    32
  )

  switch(lsuState) {
    is(LSUState.sIdle) {
      when(io.fromProcessing.fire) {
        lsuState := Mux(
          io.axi4.aw.fire || io.axi4.ar.fire,
          LSUState.sWait,
          LSUState.sRequest
        )
      }
    }
    is(LSUState.sRequest) {
      when(io.axi4.ar.fire || io.axi4.aw.fire) {
        lsuState := LSUState.sWait
      }
    }
    is(LSUState.sWait) {
      when(io.axi4.r.fire || io.axi4.b.fire) {
        lsuState := Mux(io.toProcessing.fire, LSUState.sIdle, LSUState.sSend)
      }
    }
    is(LSUState.sSend) {
      when(io.toProcessing.fire) {
        lsuState := LSUState.sIdle
      }
    }
  }

}
