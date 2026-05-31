package fecundmare

import chisel3._

import scala.math

import fecundmare.util.RegisterFileBundle

class RegisterFile(registerAddrWidth: Int) extends Module {
  val io = IO(new RegisterFileBundle)

  val registers = RegInit(
    VecInit(Seq.fill(math.pow(2, registerAddrWidth).toInt)(0.U(32.W)))
  )

  val writeValid =
    io.fromEXU.bits.writeEnable && io.fromEXU.bits.writeAddr =/= 0.U && io.fromEXU.valid
  registers(
    io.fromEXU.bits.writeAddr(registerAddrWidth - 1, 0)
  ) := Mux(
    writeValid,
    io.fromEXU.bits.writeData,
    registers(io.fromEXU.bits.writeAddr(registerAddrWidth - 1, 0))
  )

  dontTouch(writeValid)

  io.toIDU.bits.readData1 := Mux(
    io.fromIDU.bits.readAddr1 === io.fromEXU.bits.writeAddr && writeValid,
    io.fromEXU.bits.writeData,
    registers(io.fromIDU.bits.readAddr1(registerAddrWidth - 1, 0))
  )
  io.toIDU.bits.readData2 := Mux(
    io.fromIDU.bits.readAddr2 === io.fromEXU.bits.writeAddr && writeValid,
    io.fromEXU.bits.writeData,
    registers(io.fromIDU.bits.readAddr2(registerAddrWidth - 1, 0))
  )

  io.toIDU.valid := true.B
  io.fromIDU.ready := true.B
  io.fromEXU.ready := true.B
  io.fromEXU.ready := true.B
}
