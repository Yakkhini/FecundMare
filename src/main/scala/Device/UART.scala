package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.AXI4LiteBundle

object UARTState extends ChiselEnum {
  val sIdle, sPrint = Value
}

class UART extends Module {
  val io = IO(Flipped(new AXI4LiteBundle))

  val data = RegInit(0.U(8.W))
  val state = RegInit(UARTState.sIdle)

  io.aw.ready := state === UARTState.sIdle
  io.w.ready := state === UARTState.sIdle
  io.b.valid := true.B
  io.b.bits.resp := 1.U

  data := Mux(io.w.fire, io.w.bits.data, data)

  // Block read transaction
  io.ar.ready := false.B
  io.r.valid := false.B
  io.r.bits.data := 0.U
  io.r.bits.resp := 0.U

  switch(state) {
    is(UARTState.sIdle) {
      when(io.aw.fire) {
        state := UARTState.sPrint
      }
    }
    is(UARTState.sPrint) {
      printf("%c", data)
      state := UARTState.sIdle
    }
  }

}
