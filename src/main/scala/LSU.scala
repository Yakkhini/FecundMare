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
   * We skip the  Request & Wait state in two transaction and
   * the Send state in write transaction.
   *
   * Read FSM: Idle -> Wait -> Idle
   * Write FSM: Idle -> Idle
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
  val currentReadEnable = Mux(io.fromEXU.fire, io.fromEXU.bits.readEnable, readEnable)
  val currentWriteEnable = Mux(io.fromEXU.fire, io.fromEXU.bits.writeEnable, writeEnable)
  io.axi4Lite.ar.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentReadEnable
  io.axi4Lite.aw.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4Lite.w.valid := (lsuState === LSUState.sRequest || io.fromEXU.fire) && currentWriteEnable
  io.axi4Lite.ar.bits.addr := Mux(io.fromEXU.fire, io.fromEXU.bits.address, address)
  io.axi4Lite.aw.bits.addr := Mux(io.fromEXU.fire, io.fromEXU.bits.address, address)
  io.axi4Lite.w.bits.data := Mux(io.fromEXU.fire, io.fromEXU.bits.writeData, writeData)
  io.axi4Lite.w.bits.strb := io.fromEXU.bits.length

  // State 3
  // Currently we don't care about the B channel
  io.axi4Lite.r.ready := lsuState === LSUState.sWait || lsuState === LSUState.sSend // Skip the Wait state
  io.axi4Lite.b.ready := lsuState === LSUState.sWait

  // State 4
  io.toEXU.valid := lsuState === LSUState.sSend
  io.toEXU.bits.readData := io.axi4Lite.r.bits.data

  switch(lsuState) {
    is(LSUState.sIdle) {
      when(io.fromEXU.fire) {
        // We trust the SRAM handshake would done in same cycle.
        // So the Request state is skipped.
        //
        // As the write transaction, we don't care the response now.
        lsuState := Mux(io.fromEXU.bits.readEnable, LSUState.sWait, LSUState.sIdle)
      }
    }
    is(LSUState.sRequest) {
      when(io.axi4Lite.ar.fire && io.axi4Lite.aw.fire && io.axi4Lite.w.fire) {
        lsuState := LSUState.sSend
      }
    }
    is(LSUState.sWait) {
      when(io.axi4Lite.r.fire || io.axi4Lite.b.fire) {
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
