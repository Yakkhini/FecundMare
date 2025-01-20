package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.IFUBundle
import taohe.util.enum.MemSize

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

  val pc = RegInit("h20000000".U(32.W))
  val inst = RegInit(0.U(32.W))
  val iCount = RegInit(0.U(32.W))

  val ifuState = RegInit(IFUState.sRequest)

  // State 1
  io.fromEXU.ready := ifuState === IFUState.sIdle || io.axi4.r.fire
  pc := Mux(io.fromEXU.fire, io.fromEXU.bits.nextPC, pc)
  iCount := Mux(io.fromEXU.fire, iCount + 1.U, iCount)

  dontTouch(iCount)

  // State 2
  io.axi4.ar.valid := ifuState === IFUState.sRequest
  io.axi4.ar.bits.addr := pc
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.size := MemSize.W.asUInt
  io.axi4.ar.bits.burst := 0.U

  // State 3
  io.axi4.r.ready := ifuState === IFUState.sFetch
  inst := Mux(io.axi4.r.fire, io.axi4.r.bits.data, inst)
  val currentInst = Mux(io.axi4.r.fire, io.axi4.r.bits.data, inst)

  // State 4
  io.toIDU.valid := ifuState === IFUState.sSend || io.axi4.r.fire
  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := currentInst

  // Make write transaction silent
  io.axi4.aw.valid := false.B
  io.axi4.aw.bits.burst := 0.U
  io.axi4.aw.bits.addr := 0.U
  io.axi4.aw.bits.id := 0.U
  io.axi4.aw.bits.len := 0.U
  io.axi4.aw.bits.size := 0.U
  io.axi4.w.valid := false.B
  io.axi4.w.bits.data := 0.U
  io.axi4.w.bits.strb := 0.U
  io.axi4.w.bits.last := false.B
  io.axi4.b.ready := false.B

  switch(ifuState) {
    is(IFUState.sIdle) {
      when(io.fromEXU.fire) {
        // Skip the request state if the PC accepted in the same cycle.
        ifuState := Mux(io.axi4.ar.fire, IFUState.sFetch, IFUState.sRequest)
      }
    }
    is(IFUState.sRequest) {
      when(io.axi4.ar.fire) {
        ifuState := IFUState.sFetch
      }
    }
    is(IFUState.sFetch) {
      when(io.axi4.r.fire) {
        // The IFU should finish in two cycles when there is no mem access
        // instructions.
        // Normal instructions FSM: (Request -> Fetch) -> (R -> F)
        // Mem Access instructions(l*, s*) FSM: (Request -> Fetch -> Idle -> Idle) -> (...)
        ifuState := Mux(io.fromEXU.valid, IFUState.sRequest, IFUState.sIdle)
      }
    }
    is(IFUState.sSend) {
      when(io.toIDU.fire) {
        ifuState := IFUState.sIdle
      }
    }
  }

}
