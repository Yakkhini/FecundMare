package fecundmare

import chisel3._
import chisel3.util.{switch, is}

import fecundmare.util.AXI4Bundle
import fecundmare.util.PreSiliconPerformanceCounter

object AXIArbiterState extends ChiselEnum {
  val sIdle, sIFU, sLSU = Value
}

class AXIArbiterIO extends Bundle {
  val instructionFetch = Flipped(new AXI4Bundle)
  val loadStore = Flipped(new AXI4Bundle)
  val out = new AXI4Bundle
}

class AXIArbiter extends Module {

  val io = IO(new AXIArbiterIO)

  val state = RegInit(AXIArbiterState.sIdle)

  val ifuDrive =
    state === AXIArbiterState.sIFU || (state === AXIArbiterState.sIdle && io.instructionFetch.ar.valid)

  // Write transaction
  //
  // We still block the write transaction from LSU
  // when IFU is driving.
  io.out.aw.bits := io.loadStore.aw.bits
  io.out.aw.valid := io.loadStore.aw.valid && !ifuDrive
  io.loadStore.aw.ready := io.out.aw.ready && !ifuDrive

  io.out.w.bits := io.loadStore.w.bits
  io.out.w.valid := io.loadStore.w.valid && !ifuDrive
  io.loadStore.w.ready := io.out.w.ready && !ifuDrive

  io.loadStore.b.bits := io.out.b.bits
  io.loadStore.b.valid := io.out.b.valid && !ifuDrive
  io.out.b.ready := io.loadStore.b.ready && !ifuDrive

  // Read transaction
  io.out.ar.bits := Mux(
    ifuDrive,
    io.instructionFetch.ar.bits,
    io.loadStore.ar.bits
  )
  io.out.ar.valid := Mux(
    ifuDrive,
    io.instructionFetch.ar.valid,
    io.loadStore.ar.valid
  )
  io.instructionFetch.ar.ready := io.out.ar.ready && ifuDrive
  io.loadStore.ar.ready := io.out.ar.ready && !ifuDrive

  io.out.r.ready := Mux(
    ifuDrive,
    io.instructionFetch.r.ready,
    io.loadStore.r.ready
  )
  io.instructionFetch.r.valid := io.out.r.valid && ifuDrive
  io.loadStore.r.valid := io.out.r.valid && !ifuDrive
  io.instructionFetch.r.bits := io.out.r.bits
  io.loadStore.r.bits := io.out.r.bits

  // IFU no need to write data
  io.instructionFetch.aw.ready := false.B
  io.instructionFetch.w.ready := false.B
  io.instructionFetch.b.valid := false.B
  io.instructionFetch.b.bits.resp := 0.U
  io.instructionFetch.b.bits.id := 0.U

  // Performance Counter
  val ifuAXIWaiting =
    state === AXIArbiterState.sIFU && io.instructionFetch.r.ready && !io.instructionFetch.r.fire
  val ifuArbiterWaiting =
    state =/= AXIArbiterState.sIFU && io.instructionFetch.r.ready
  PreSiliconPerformanceCounter("ifuAXIWaitingCycleCounter", ifuAXIWaiting, 32)
  PreSiliconPerformanceCounter(
    "ifuArbiterWaitingCycleCounter",
    ifuArbiterWaiting,
    32
  )

  val lsuAXILoadWaiting =
    !ifuDrive && io.loadStore.r.ready && !io.loadStore.r.fire
  val lsuAXIStoreWaiting =
    !ifuDrive && io.loadStore.b.ready && !io.loadStore.b.fire
  val lsuArbiterLoadWaiting = ifuDrive && io.loadStore.r.ready
  val lsuArbiterStoreWaiting = ifuDrive && io.loadStore.b.ready
  PreSiliconPerformanceCounter(
    "lsuAXILoadWaitingCycleCounter",
    lsuAXILoadWaiting,
    32
  )
  PreSiliconPerformanceCounter(
    "lsuAXIStoreWaitingCycleCounter",
    lsuAXIStoreWaiting,
    32
  )
  PreSiliconPerformanceCounter(
    "lsuArbiterLoadWaitingCycleCounter",
    lsuArbiterLoadWaiting,
    32
  )
  PreSiliconPerformanceCounter(
    "lsuArbiterStoreWaitingCycleCounter",
    lsuArbiterStoreWaiting,
    32
  )

  switch(state) {
    is(AXIArbiterState.sIdle) {
      when(
        io.instructionFetch.ar.valid || io.loadStore.ar.valid || io.loadStore.aw.valid
      ) {
        state := Mux(
          io.instructionFetch.ar.valid,
          AXIArbiterState.sIFU,
          AXIArbiterState.sLSU
        )
      }
    }
    is(AXIArbiterState.sIFU) {
      when(io.instructionFetch.r.fire && io.instructionFetch.r.bits.last) {
        state := Mux(
          io.loadStore.ar.valid || io.loadStore.aw.valid,
          AXIArbiterState.sLSU,
          AXIArbiterState.sIdle
        )
      }
    }
    is(AXIArbiterState.sLSU) {
      when(io.loadStore.r.fire || io.loadStore.b.fire) {
        state := Mux(
          io.instructionFetch.ar.valid,
          AXIArbiterState.sIFU,
          AXIArbiterState.sIdle
        )
      }
    }
  }

}
