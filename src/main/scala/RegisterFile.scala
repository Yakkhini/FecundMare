package taohe

import chisel3._

import taohe.util.RegisterFileBundle
import chisel3.probe.Probe

class RegisterFile extends Module {
  val io = IO(new RegisterFileBundle)

  val registers = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  when(
    io.fromEXU.bits.writeEnable && io.fromEXU.bits.writeAddr =/= 0.U && io.fromEXU.valid
  ) {
    registers(io.fromEXU.bits.writeAddr) := io.fromEXU.bits.writeData
  }

  io.toEXU.bits.readData1 := registers(io.fromEXU.bits.readAddr1)
  io.toEXU.bits.readData2 := registers(io.fromEXU.bits.readAddr2)

  io.toEXU.valid := false.B
  io.fromEXU.ready := false.B
  io.fromEXU.ready := false.B
}
