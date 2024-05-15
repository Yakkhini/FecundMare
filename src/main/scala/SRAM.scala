package taohe

import chisel3._

import taohe.dpic.MemRead
import chisel3.util.{switch, is}

class SRAM extends Module {
  val io = IO(new Bundle {
    val readAddr = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
    val valid = Output(Bool())
  })

  val addr = RegInit(0.U(32.W))
  addr := io.readAddr

  io.valid := io.readAddr === addr

  val memRead = Module(new MemRead())

  memRead.io.readAddr := io.readAddr
  io.readData := memRead.io.readData
}
