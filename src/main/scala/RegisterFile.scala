package taohe

import chisel3._

import taohe.util.RegisterFileBundle
import chisel3.probe.Probe

class RegisterFile extends Module {
  val io = IO(new RegisterFileBundle)

  val registers = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  when(io.withEXU.writeEnable && io.fromIDU.writeAddr =/= 0.U) {
    registers(io.fromIDU.writeAddr) := io.withEXU.writeData
  }

  io.withEXU.readData1 := registers(io.fromIDU.readAddr1)
  io.withEXU.readData2 := registers(io.fromIDU.readAddr2)
}
