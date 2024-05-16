package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.dpic.{MemRead, MemWrite}
import taohe.util.enum.MemLen

class SRAM extends Module {
  val io = IO(new Bundle {
    val readAddr = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
    val writeAddr = Input(UInt(32.W))
    val writeData = Input(UInt(32.W))
    val writeLen = Input(UInt(MemLen.getWidth.W))
    val writeEnable = Input(Bool())
    val valid = Output(Bool())
  })

  val addr = RegInit(0.U(32.W))
  addr := io.readAddr

  io.valid := io.readAddr === addr

  val memRead = Module(new MemRead())
  val memWrite = Module(new MemWrite())

  memRead.io.readAddr := io.readAddr
  io.readData := memRead.io.readData

  memWrite.io.writeAddr := io.writeAddr
  memWrite.io.writeData := io.writeData
  memWrite.io.writeLen := io.writeLen
  memWrite.io.writeEnable := io.writeEnable
  memWrite.io.clock := clock
}
