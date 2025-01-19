package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.LSUBundle

object LSUState extends ChiselEnum {
  /*
   * Currently in multi-cycle design, there is
   * no need to distinguish between read and write
   * transaction. Only one type of transaction is
   * allowed in one cycle.
   *
   * LSU State
   *
   * 1. Idle State
   * 2. Request State
   * 3. Wait State
   * 4. Send State
   *
   * Now our design is connenct with a SRAM module.
   * So the read transaction should finish in two
   * cycles, and write transaction should finish in
   * one cycle. Maybe few states have to be skipped.
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
  val address = RegInit(0.U(32.W))
  val length = RegInit(0.U(32.W))
  val writeData = RegInit(0.U(32.W))
  val writeEnable = RegInit(false.B)
  val readEnable = RegInit(false.B)

  // State 1
  io.fromEXU.ready := lsuState === LSUState.sIdle
  address := Mux(io.fromEXU.fire, io.fromEXU.bits.address, address)
  length := Mux(io.fromEXU.fire, io.fromEXU.bits.length, length)
  writeData := Mux(io.fromEXU.fire, io.fromEXU.bits.writeData, writeData)
  writeEnable := Mux(io.fromEXU.fire, io.fromEXU.bits.writeEnable, writeEnable)
  readEnable := Mux(io.fromEXU.fire, io.fromEXU.bits.readEnable, readEnable)

  // State 2
  val currentReadEnable =
    Mux(io.fromEXU.fire, io.fromEXU.bits.readEnable, readEnable)
  val currentWriteEnable =
    Mux(io.fromEXU.fire, io.fromEXU.bits.writeEnable, writeEnable)
  io.axi4.ar.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentReadEnable
  io.axi4.aw.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4.w.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4.w.bits.last := io.axi4.w.valid
  io.axi4.ar.bits.addr := Mux(
    io.fromEXU.fire,
    io.fromEXU.bits.address,
    address
  )
  io.axi4.aw.bits.addr := Mux(
    io.fromEXU.fire,
    io.fromEXU.bits.address,
    address
  )
  io.axi4.w.bits.data := Mux(
    io.fromEXU.fire,
    io.fromEXU.bits.writeData,
    writeData
  )
  io.axi4.w.bits.strb := io.fromEXU.bits.length
  io.axi4.aw.bits.burst := 0.U
  io.axi4.aw.bits.id := 0.U
  io.axi4.aw.bits.len := 0.U
  io.axi4.aw.bits.size := 0.U
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.size := "b010".U
  io.axi4.ar.bits.burst := 0.U

  // State 3
  // Currently we don't care about the B channel
  io.axi4.r.ready := lsuState === LSUState.sWait || lsuState === LSUState.sSend // Skip the Wait state
  io.axi4.b.ready := lsuState === LSUState.sWait

  // State 4
  io.toEXU.valid := lsuState === LSUState.sSend || (lsuState === LSUState.sIdle && !currentWriteEnable && !currentReadEnable) || (lsuState === LSUState.sWait && io.axi4.r.fire)
  io.toEXU.bits.readData := io.axi4.r.bits.data

  switch(lsuState) {
    is(LSUState.sIdle) {
      when(io.fromEXU.fire) {
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
        lsuState := Mux(io.toEXU.fire, LSUState.sIdle, LSUState.sSend)
      }
    }
    is(LSUState.sSend) {
      when(io.toEXU.fire) {
        lsuState := LSUState.sIdle
      }
    }
  }

}
