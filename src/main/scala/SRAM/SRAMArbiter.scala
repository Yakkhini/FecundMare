package taohe

import chisel3._
import taohe.util.AXI4LiteBundle
import chisel3.util.Arbiter
import chisel3.util.{switch, is}

object SRAMArbiterState extends ChiselEnum {
  val sIdle, sIFU, sLSU = Value
}

class SRAMArbiterIO extends Bundle {
  val ifu = Flipped(new AXI4LiteBundle)
  val lsu = Flipped(new AXI4LiteBundle)
  val sram = new AXI4LiteBundle
}

class SRAMArbiter extends Module {

  val io = IO(new SRAMArbiterIO)

  val state = RegInit(SRAMArbiterState.sIdle)

  val ifuDrive =
    state === SRAMArbiterState.sIFU || (state === SRAMArbiterState.sIdle && io.ifu.ar.valid)

  // Write transaction
  //
  // We still block the write transaction from LSU
  // when IFU is driving.
  io.sram.aw.bits := io.lsu.aw.bits
  io.sram.aw.valid := io.lsu.aw.valid && !ifuDrive
  io.lsu.aw.ready := io.sram.aw.ready && !ifuDrive

  io.sram.w.bits := io.lsu.w.bits
  io.sram.w.valid := io.lsu.w.valid && !ifuDrive
  io.lsu.w.ready := io.sram.w.ready && !ifuDrive

  io.lsu.b.bits := io.sram.b.bits
  io.lsu.b.valid := io.sram.b.valid && !ifuDrive
  io.sram.b.ready := io.lsu.b.ready && !ifuDrive

  // Read transaction
  io.sram.ar.bits := Mux(ifuDrive, io.ifu.ar.bits, io.lsu.ar.bits)
  io.sram.ar.valid := Mux(ifuDrive, io.ifu.ar.valid, io.lsu.ar.valid)
  io.ifu.ar.ready := io.sram.ar.ready && ifuDrive
  io.lsu.ar.ready := io.sram.ar.ready && !ifuDrive

  io.sram.r.ready := Mux(ifuDrive, io.ifu.r.ready, io.lsu.r.ready)
  io.ifu.r.valid := io.sram.r.valid && ifuDrive
  io.lsu.r.valid := io.sram.r.valid && !ifuDrive
  io.ifu.r.bits := io.sram.r.bits
  io.lsu.r.bits := io.sram.r.bits

  // IFU no need to write data
  io.ifu.aw.ready := false.B
  io.ifu.w.ready := false.B
  io.ifu.b.valid := false.B
  io.ifu.b.bits.resp := 0.U

  switch(state) {
    is(SRAMArbiterState.sIdle) {
      when(io.ifu.ar.valid) {
        state := SRAMArbiterState.sIFU
      }.elsewhen(io.lsu.ar.valid) {
        state := SRAMArbiterState.sLSU
      }
    }
    is(SRAMArbiterState.sIFU) {
      when(io.ifu.r.fire) {
        state := Mux(
          io.lsu.ar.valid || io.lsu.aw.valid,
          SRAMArbiterState.sLSU,
          SRAMArbiterState.sIdle
        )
      }
    }
    is(SRAMArbiterState.sLSU) {
      when(io.lsu.r.fire || io.lsu.aw.fire) {
        state := Mux(
          io.ifu.ar.valid,
          SRAMArbiterState.sIFU,
          SRAMArbiterState.sIdle
        )
      }
    }
  }

}
