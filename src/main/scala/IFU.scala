package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

import taohe.util.IFUBundle
import taohe.util.enum.UnitState
import chisel3.util.{switch, is}

class IFU extends Module {
  val io = IO(new IFUBundle)

  val pc = RegInit("h80000000".U(32.W))
  val inst = Wire(UInt(32.W))

  val sram = Module(new SRAM())
  val sramValid = Wire(Bool())

  // Default value
  io.toIDU.valid := sramValid
  io.fromEXU.ready := false.B

  pc := Mux(io.fromEXU.valid, io.fromEXU.bits.nextPC, pc)

  sram.io.readAddr := pc
  sramValid := sram.io.valid
  inst := sram.io.readData

  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := inst
}
