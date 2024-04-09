package taohe

import chisel3._, chisel3.util._

class RegisterFile extends Module {
  val io = IO(new Bundle {
    val readAddr1 = Input(UInt(5.W))
    val readAddr2 = Input(UInt(5.W))
    val writeAddr = Input(UInt(5.W))
    val writeData = Input(UInt(32.W))
    val writeEnable = Input(Bool())
    val readData1 = Output(UInt(32.W))
    val readData2 = Output(UInt(32.W))
  })

  val registers = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  when(io.writeEnable) {
    registers(io.writeAddr) := io.writeData
  }

  io.readData1 := registers(io.readAddr1)
  io.readData2 := registers(io.readAddr2)
}
