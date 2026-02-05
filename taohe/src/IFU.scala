package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.IFUBundle
import taohe.util.PerformanceCounter

object IFUState extends ChiselEnum {
  /*
   *  IFU FSM State
   *
   *  1. Work State:
   *    * Check inst type for PC self updating;
   *    * Send ICache request if next PC available;
   *    * Receive inst from ICache and send it to IDU.
   *    * Send to IDU and produce supply when there is no stall:
   *      - Next PC available;
   *      - Receive inst from ICache valid.
   *  2. Send State: Send inst to IDU only;
   *  3. Wait State: Wait EXU to provide next PC;
   *  4. Request State: Send ICache request only.
   *
   */
  val sWork, sWait, sRequest = Value
}

/*
 *  Instruction Fetch Unit
 *
 * */
class IFU extends Module {
  val io = IO(new IFUBundle)

  val pc = RegInit("h30000000".U(32.W))
  val inst = WireInit(0.U(32.W))
  val state = RegInit(IFUState.sRequest)

  val stall = Wire(Bool())

  // Inst Retire
  val iCount = RegInit(0.U(32.W))
  val diffNextPC = RegInit(0.U(32.W))

  iCount := Mux(io.fromEXU.fire, iCount + 1.U, iCount)
  diffNextPC := Mux(io.fromEXU.fire, io.fromEXU.bits.nextPC, diffNextPC)
  dontTouch(iCount)
  dontTouch(diffNextPC)

  // Pre-Decoding for Self PC Update
  val normalNextPC =
    (inst(6, 0) =/= "b1100011".U) && (inst(6, 0) =/= "b1100111".U) && (inst(
      6,
      0
    ) =/= "b1101111".U) && (inst(6, 0) =/= "b1110011".U)
  val useEXUNextPC = io.fromEXU.valid && io.fromEXU.bits.prevPC === pc

  val updatePC =
    (!stall && io.toIDU.fire) || (state === IFUState.sWait && useEXUNextPC)
  val nextPC = Mux(normalNextPC, pc + 4.U, io.fromEXU.bits.nextPC)
  pc := Mux(updatePC, nextPC, pc)

  io.fromEXU.ready := true.B

  // Send ICache request
  io.toICache.valid := ((!stall && io.toIDU.fire) || state === IFUState.sRequest) && !reset.asBool
  io.toICache.bits.pc := Mux(IFUState.sRequest === state, pc, nextPC)

  // Receive Inst from ICache
  io.fromICache.ready := !stall && io.toIDU.fire
  inst := io.fromICache.bits.readInst

  // Send Inst to IDU
  io.toIDU.valid := state === IFUState.sWork && (!stall || (!normalNextPC && io.fromICache.valid))
  io.toIDU.bits.currentPC := Mux(state === IFUState.sRequest, 0.U, pc)
  io.toIDU.bits.inst := inst

  // Stall Condition
  stall := state =/= IFUState.sWork || !io.fromICache.valid || (!normalNextPC && !useEXUNextPC)

  // Performance Counter
  val fetchInstNumCounter = PerformanceCounter(io.fromICache.fire, 32)
  val fetchWaitingCycleCounter =
    PerformanceCounter(io.fromICache.ready && !io.fromICache.fire, 32)

  switch(state) {
    is(IFUState.sWork) {
      when(stall && !normalNextPC && io.fromICache.valid && io.toIDU.fire) {
        state := IFUState.sWait
      }
    }
    is(IFUState.sWait) {
      when(useEXUNextPC) {
        state := IFUState.sRequest
      }
    }
    is(IFUState.sRequest) {
      when(io.toICache.fire) {
        state := IFUState.sWork
      }
    }
  }

}
