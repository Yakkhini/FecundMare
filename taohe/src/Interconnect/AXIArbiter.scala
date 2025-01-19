package taohe

import chisel3._
import taohe.util.{AXI4LiteBundle, AXI4Bundle}
import chisel3.util.{switch, is}

object AXIArbiterState extends ChiselEnum {
  val sIdle, sIFU, sLSU = Value
}

class AXIArbiterIO extends Bundle {
  val ifu = Flipped(new AXI4Bundle)
  val lsu = Flipped(new AXI4Bundle)
  val out = new AXI4Bundle
}

class AXIArbiter extends Module {

  val io = IO(new AXIArbiterIO)

  val state = RegInit(AXIArbiterState.sIdle)

  val ifuDrive =
    state === AXIArbiterState.sIFU || (state === AXIArbiterState.sIdle && io.ifu.ar.valid)

  // Write transaction
  //
  // We still block the write transaction from LSU
  // when IFU is driving.
  io.out.aw.bits := io.lsu.aw.bits
  io.out.aw.valid := io.lsu.aw.valid && !ifuDrive
  io.lsu.aw.ready := io.out.aw.ready && !ifuDrive

  io.out.w.bits := io.lsu.w.bits
  io.out.w.valid := io.lsu.w.valid && !ifuDrive
  io.lsu.w.ready := io.out.w.ready && !ifuDrive

  io.lsu.b.bits := io.out.b.bits
  io.lsu.b.valid := io.out.b.valid && !ifuDrive
  io.out.b.ready := io.lsu.b.ready && !ifuDrive

  // Read transaction
  io.out.ar.bits := Mux(ifuDrive, io.ifu.ar.bits, io.lsu.ar.bits)
  io.out.ar.valid := Mux(ifuDrive, io.ifu.ar.valid, io.lsu.ar.valid)
  io.ifu.ar.ready := io.out.ar.ready && ifuDrive
  io.lsu.ar.ready := io.out.ar.ready && !ifuDrive

  io.out.r.ready := Mux(ifuDrive, io.ifu.r.ready, io.lsu.r.ready)
  io.ifu.r.valid := io.out.r.valid && ifuDrive
  io.lsu.r.valid := io.out.r.valid && !ifuDrive
  io.ifu.r.bits := io.out.r.bits
  io.lsu.r.bits := io.out.r.bits

  // IFU no need to write data
  io.ifu.aw.ready := false.B
  io.ifu.w.ready := false.B
  io.ifu.b.valid := false.B
  io.ifu.b.bits.resp := 0.U
  io.ifu.b.bits.id := 0.U

  switch(state) {
    is(AXIArbiterState.sIdle) {
      when(io.ifu.ar.valid) {
        state := AXIArbiterState.sIFU
      }.elsewhen(io.lsu.ar.valid) {
        state := AXIArbiterState.sLSU
      }
    }
    is(AXIArbiterState.sIFU) {
      when(io.ifu.r.fire) {
        state := Mux(
          io.lsu.ar.valid || io.lsu.aw.valid,
          AXIArbiterState.sLSU,
          AXIArbiterState.sIdle
        )
      }
    }
    is(AXIArbiterState.sLSU) {
      when(io.lsu.r.fire || io.lsu.aw.fire) {
        state := Mux(
          io.ifu.ar.valid,
          AXIArbiterState.sIFU,
          AXIArbiterState.sIdle
        )
      }
    }
  }

}
