package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.IFUBundle

object IFUState extends ChiselEnum {
  /*
   * IFU FSM State
   *
   * 1. Idle State: Wait for EXU to send next pc value.
   * 2. Request State: IFU send a request to SRAM to fetch
   * the instruction.
   * 3. Fetch State: IFU is waiting for the instruction.
   * 4. Send State: The PC signal and Inst signal is valid
   * for IDU.
   *
   */
  val sIdle, sRequest, sFetch, sSend = Value
}

class IFU extends Module {
  val io = IO(new IFUBundle)

  val pc = RegInit("h80000000".U(32.W))
  val inst = RegInit(0.U(32.W))

  val ifuState = RegInit(IFUState.sIdle)

  // State 1
  io.fromEXU.ready := ifuState === IFUState.sIdle
  pc := Mux(io.fromEXU.fire, io.fromEXU.bits.nextPC, pc)
  val currentPC = Mux(io.fromEXU.fire, io.fromEXU.bits.nextPC, pc)

  // State 2
  io.axi4Lite.ar.valid := ifuState === IFUState.sRequest || io.fromEXU.fire
  io.axi4Lite.ar.bits.addr := currentPC

  // State 3
  io.axi4Lite.r.ready := ifuState === IFUState.sFetch
  val currentInst = Mux(io.axi4Lite.r.fire, io.axi4Lite.r.bits.data, inst)

  // State 4
  io.toIDU.valid := ifuState === IFUState.sSend || io.axi4Lite.r.fire
  io.toIDU.bits.currentPC := currentPC
  io.toIDU.bits.inst := currentInst

  // Make write transaction silent
  io.axi4Lite.aw.valid := false.B
  io.axi4Lite.aw.bits.addr := 0.U
  io.axi4Lite.w.valid := false.B
  io.axi4Lite.w.bits.data := 0.U
  io.axi4Lite.w.bits.strb := 0.U
  io.axi4Lite.b.ready := false.B

  switch(ifuState) {
    is(IFUState.sIdle) {
      when(io.fromEXU.fire) {
        // Skip the request state if the PC accepted in the same cycle.
        ifuState := Mux(io.axi4Lite.ar.fire, IFUState.sFetch, IFUState.sRequest)
      }
    }
    is(IFUState.sRequest) {
      when(io.axi4Lite.ar.fire) {
        ifuState := IFUState.sFetch
      }
    }
    is(IFUState.sFetch) {
      when(io.axi4Lite.r.fire) {
        // Skip the send state if the instruction accepted in the same cycle.
        // We can believe that the skip behavior is more common circumstance.
        ifuState := Mux(io.toIDU.fire, IFUState.sIdle, IFUState.sSend)
      }
    }
    is(IFUState.sSend) {
      when(io.toIDU.fire) {
        ifuState := IFUState.sIdle
      }
    }
  }

}
