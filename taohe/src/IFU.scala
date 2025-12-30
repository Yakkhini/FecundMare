package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.IFUBundle
import taohe.util.enum.MemSize
import taohe.util.PerformanceCounter

object IFUState extends ChiselEnum {
  /*
   * IFU FSM State
   *
   * 1. Idle State: Wait for EXU to send next pc value.
   * 2. Cache Request State: IFU send a request to Cache
   * to fetch the instruction.
   * 3. Memory Request State IFU send a request memory.
   * 4. Fetch State: IFU is waiting for the instruction.
   * 5. Send State: The PC signal and Inst signal is valid
   * for IDU and ICache.
   *
   */
  val sIdle, sCacheRespond, sRequest, sFetch, sSend = Value
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
  io.fromEXU.ready := ifuState === IFUState.sIdle || io.axi4.r.fire
  pc := Mux(io.fromEXU.fire, io.fromEXU.bits.nextPC, pc)
  iCount := Mux(io.fromEXU.fire, iCount + 1.U, iCount)

  io.toICache.valid := (io.fromEXU.fire && !reset.asBool) || (ifuState === IFUState.sSend)

  val iCacheQueryPC = io.fromEXU.bits.nextPC

  dontTouch(iCount)

  // State 2
  io.fromICache.ready := ifuState === IFUState.sCacheRespond
  val iCacheHit = RegInit(false.B)
  val iCacheInst = RegInit(0.U(32.W))
  iCacheHit := ifuState === IFUState.sCacheRespond && io.fromICache.bits.hit
  iCacheInst := Mux(
    ifuState === IFUState.sCacheRespond,
    io.fromICache.bits.readData,
    iCacheInst
  )

  val state2ARValid =
    (ifuState === IFUState.sCacheRespond) && !io.fromICache.bits.hit && !reset.asBool

  // State 3
  val state3ARValid = (ifuState === IFUState.sRequest) && !reset.asBool
  io.axi4.ar.valid := state2ARValid || state3ARValid
  io.axi4.ar.bits.addr := pc
  io.axi4.ar.bits.id := 0.U
  io.axi4.ar.bits.len := 0.U
  io.axi4.ar.bits.size := MemSize.W.asUInt
  io.axi4.ar.bits.burst := 0.U

  // State 4
  io.axi4.r.ready := (ifuState === IFUState.sFetch) && !reset.asBool
  val axiInst = Mux(io.axi4.r.fire, io.axi4.r.bits.data, inst)

  inst := Mux(iCacheHit, iCacheInst, axiInst)
  val currentInst = Mux(iCacheHit, iCacheInst, axiInst)

  val receiveFenceInstruction = inst(6, 0) === "b0001111".U
  val nopInstruction = inst === "h00000013".U

  val physicalInstruction = Mux(
    receiveFenceInstruction,
    nopInstruction,
    inst
  )

  // State 5
  io.toIDU.valid := {
    if (physicalVersion) (ifuState === IFUState.sSend)
    else (ifuState === IFUState.sSend || io.axi4.r.fire || iCacheHit)
  }
  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := {
    if (physicalVersion) physicalInstruction
    else currentInst
  }

  val iCacheWritePC = pc

  io.toICache.valid := ifuState === IFUState.sSend || io.axi4.r.fire || iCacheHit
  io.toICache.bits.writeEnable := io.axi4.r.fire
  io.toICache.bits.writeData := currentInst

  io.toICache.bits.pc := Mux(
    ifuState === IFUState.sIdle,
    iCacheQueryPC,
    iCacheWritePC
  )

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

  // Performance Counter
  val receiveInstFromCache =
    (ifuState === IFUState.sCacheRespond) && io.fromICache.bits.hit
  val fetchInstNumCounter =
    PerformanceCounter(io.axi4.r.fire || receiveInstFromCache, 32)
  val fetchWaitingCycleCounter =
    PerformanceCounter(io.axi4.r.ready && !io.axi4.r.fire, 32)
  val iCacheHitCounter =
    PerformanceCounter(receiveInstFromCache, 32)

  switch(ifuState) {
    is(IFUState.sIdle) {
      when(io.fromEXU.fire && !reset.asBool) {
        ifuState := IFUState.sCacheRespond
      }
    }
    is(IFUState.sCacheRespond) {

      val memoryRequestState =
        Mux(io.axi4.ar.fire, IFUState.sFetch, IFUState.sRequest)

      ifuState := Mux(
        io.fromICache.bits.hit,
        IFUState.sSend,
        memoryRequestState
      )
    }
    is(IFUState.sRequest) {
      when(io.axi4.ar.fire && !reset.asBool) {
        ifuState := IFUState.sFetch
      }
    }
    is(IFUState.sFetch) {
      when(io.axi4.r.fire && !reset.asBool) {
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
