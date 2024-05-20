package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.dpic.{MemRead, MemWrite}
import taohe.util.enum.MemLen
import taohe.util.SRAMBundle

class SRAM extends Module {
  val io = IO(new SRAMBundle)

  val addr = RegInit(0.U(32.W))
  addr := io.readAddr

  io.valid := io.readAddr === addr

  val memRead = Module(new MemRead())
  val memWrite = Module(new MemWrite())

  memRead.io.readAddr := io.readAddr
  io.readData := Mux(io.valid, memRead.io.readData, 0.U)

  memWrite.io.writeAddr := io.writeAddr
  memWrite.io.writeData := io.writeData
  memWrite.io.writeLen := io.writeLen
  memWrite.io.writeEnable := io.writeEnable
  memWrite.io.clock := clock
}
