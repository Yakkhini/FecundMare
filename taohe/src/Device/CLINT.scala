package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.AXI4LiteBundle

object CLINTState extends ChiselEnum {
  val sIdle, sSend = Value
}

class CLINT extends Module {
  val io = IO(Flipped(new AXI4LiteBundle))

  val state = RegInit(CLINTState.sIdle)

  val mtime = RegInit(0.U(64.W))
  mtime := mtime + 1.U

  io.ar.ready := state === CLINTState.sIdle
  io.r.valid := state === CLINTState.sSend
  io.r.bits.resp := 1.U

  // CLINT 0xa0000048(low) 0xa000004c(high)
  io.r.bits.data := Mux(
    io.ar.bits.addr === "ha0000048".U,
    mtime(31, 0),
    mtime(63, 32)
  )

  // Block write transaction
  io.aw.ready := false.B
  io.w.ready := false.B
  io.b.valid := false.B
  io.b.bits.resp := 0.U

  switch(state) {
    is(CLINTState.sIdle) {
      when(io.ar.fire) {
        state := CLINTState.sSend
      }
    }
    is(CLINTState.sSend) {
      when(io.r.fire) {
        state := CLINTState.sIdle
      }
    }
  }

}
