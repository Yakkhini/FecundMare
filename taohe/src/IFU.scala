package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.IFUBundle
import taohe.util.PerformanceCounter

object IFUState extends ChiselEnum {
  /*
   * IFU FSM State
   *
   * 1. Idle State: Wait for EXU to send next pc value.
   * 2. Request State: IFU send a request to ICache to
   * fetch the instruction.
   * 3. Fetch State: IFU is waiting for the instruction.
   * 4. Send State: The PC signal and Inst signal is valid
   * for IDU.
   *
   */
  val sIdle, sRequest, sFetch, sSend = Value
}

/*
 *  Instruction Fetch Unit
 *
 *  Data will not pass to IDU when io fire immediately
 *  since the physical design complain a combinational loop.
 *
 * */
class IFU(physicalVersion: Boolean) extends Module {
  val io = IO(new IFUBundle)

  val pc = RegInit("h30000000".U(32.W))
  val inst = RegInit(0.U(32.W))
  val iCount = RegInit(0.U(32.W))

  val ifuState = RegInit(IFUState.sRequest)

  // State 1
  io.fromEXU.ready := ifuState === IFUState.sIdle || io.fromICache.fire

  val updatePC =
    (ifuState === IFUState.sIdle && io.fromIDU.normalNextPC) || (io.fromEXU.fire && io.fromEXU.bits.prevPC === pc)
  val nextPC = Mux(io.fromIDU.normalNextPC, pc + 4.U, io.fromEXU.bits.nextPC)
  pc := Mux(updatePC, nextPC, pc)
  iCount := Mux(io.fromEXU.fire, iCount + 1.U, iCount)

  dontTouch(iCount)

  // State 2
  io.toICache.valid := (ifuState === IFUState.sRequest) && !reset.asBool
  io.toICache.bits.pc := pc

  // State 3
  io.fromICache.ready := (ifuState === IFUState.sFetch) && !reset.asBool
  inst := Mux(io.fromICache.fire, io.fromICache.bits.readInst, inst)
  val currentInst = Mux(io.fromICache.fire, io.fromICache.bits.readInst, inst)

  // State 4
  io.toIDU.valid := {
    if (physicalVersion) (ifuState === IFUState.sSend)
    else (ifuState === IFUState.sSend || io.fromICache.fire)
  }
  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := currentInst

  // Performance Counter
  val fetchInstNumCounter = PerformanceCounter(io.fromICache.fire, 32)
  val fetchWaitingCycleCounter =
    PerformanceCounter(io.fromICache.ready && !io.fromICache.fire, 32)

  switch(ifuState) {
    is(IFUState.sIdle) {
      when(updatePC && !reset.asBool) {
        // Skip the request state if the PC accepted in the same cycle.
        ifuState := Mux(io.toICache.fire, IFUState.sFetch, IFUState.sRequest)
      }
    }
    is(IFUState.sRequest) {
      when(io.toICache.fire && !reset.asBool) {
        ifuState := IFUState.sFetch
      }
    }
    is(IFUState.sFetch) {
      when(io.fromICache.fire && !reset.asBool) {
        ifuState := {
          if (physicalVersion) IFUState.sSend
          else Mux(io.fromEXU.valid, IFUState.sRequest, IFUState.sIdle)
        }
      }
    }
    is(IFUState.sSend) {
      when(io.toIDU.fire && !reset.asBool) {
        ifuState := IFUState.sIdle
      }
    }
  }

}
