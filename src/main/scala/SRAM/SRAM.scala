package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.dpic.{MemRead, MemWrite}
import taohe.util.enum.MemLen
import taohe.util.SRAMBundle

class SRAM extends Module {
  val io = IO(new SRAMBundle)

  val addr = RegInit(0.U(32.W))
  addr := io.input.bits.readAddr

  io.output.valid := io.input.bits.readAddr === addr
  io.input.ready := false.B

  val memRead = Module(new MemRead())
  val memWrite = Module(new MemWrite())

  memRead.io.readAddr := io.input.bits.readAddr
  io.output.bits.readData := Mux(io.output.valid, memRead.io.readData, 0.U)

  memWrite.io.writeAddr := io.input.bits.writeAddr
  memWrite.io.writeData := io.input.bits.writeData
  memWrite.io.writeLen := io.input.bits.writeLen
  memWrite.io.writeEnable := io.input.bits.writeEnable
  memWrite.io.clock := clock
}
