package taohe

import chisel3._

import taohe.util.AXI4LiteBundle

class CrossBarIO extends Bundle {
  val in = Flipped(new AXI4LiteBundle)

  /*
   * CrossBar output order convention:
   *
   * 0. SRAM
   * 1. UART 0xa00003f8
   * 2. CLINT 0xa0000048 0xa000004c
   *
   */
  val out = Vec(3, new AXI4LiteBundle)
}

// Maybe we will make xbar more flex to
// expand the number of ports in future
// missions during SoC development.
class CrossBar extends Module {

  val io = IO(new CrossBarIO)
  val outMatchVec = Wire(Vec(3, Bool()))
  val chosen = Wire(UInt())

  val difftestSkip =
    (outMatchVec(1) || outMatchVec(2)) && (io.in.ar.valid || io.in.aw.valid)

  dontTouch(difftestSkip)

  outMatchVec(0) := !outMatchVec(1) && !outMatchVec(2) // SRAM is default output
  outMatchVec(1) := io.in.ar.bits.addr === "ha00003f8".U // UART
  outMatchVec(
    2
  ) := io.in.ar.bits.addr === "ha0000048".U || io.in.ar.bits.addr === "ha000004c".U // CLINT

  chosen := 0.U

  for (i <- 0 to 2) {
    when(outMatchVec(i)) {
      chosen := i.U
    }
  }

  io.in <> io.out(chosen)

  for (i <- 0 to 2) {
    io.out(i).ar.valid := io.in.ar.valid && outMatchVec(i)
    io.out(i).aw.valid := io.in.aw.valid && outMatchVec(i)
    io.out(i).w.valid := io.in.w.valid && outMatchVec(i)
    io.out(i).r.ready := io.in.r.ready && outMatchVec(i)
    io.out(i).b.ready := io.in.b.ready && outMatchVec(i)

    io.out(i).ar.bits.addr := Mux(outMatchVec(i), io.in.ar.bits.addr, 0.U)
    io.out(i).aw.bits.addr := Mux(outMatchVec(i), io.in.aw.bits.addr, 0.U)
    io.out(i).w.bits.data := Mux(outMatchVec(i), io.in.w.bits.data, 0.U)
    io.out(i).w.bits.strb := Mux(outMatchVec(i), io.in.w.bits.strb, 0.U)

  }

}
