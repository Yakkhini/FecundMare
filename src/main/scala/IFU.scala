package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

import taohe.util.IFUBundle

class IFU extends Module {
  val io = IO(new IFUBundle)

  io.toIDU.valid := false.B
  io.fromEXU.ready := false.B

  val pc = RegInit("h80000000".U(32.W))
  val inst = Wire(UInt(32.W))

  val sram = Module(new SRAM())

  sram.io.readAddr := pc
  inst := sram.io.readData

  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := inst

  pc := io.fromEXU.bits.nextPC
}
