package taohe

import chisel3._

import taohe.dpic.MemRead

class SRAM extends Module {
  val io = IO(new Bundle {
    val readAddr = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
  })

  val memRead = Module(new MemRead())

  memRead.io.readAddr := io.readAddr
  io.readData := memRead.io.readData
}
