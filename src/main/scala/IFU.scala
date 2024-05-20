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

  val sramValid = Wire(Bool())

  // Default value
  io.toIDU.valid := sramValid
  io.fromEXU.ready := false.B

  pc := Mux(io.fromEXU.valid, io.fromEXU.bits.nextPC, pc)

  io.toSRAM.bits.readAddr := pc
  io.toSRAM.bits.writeAddr := 0.U
  io.toSRAM.bits.writeData := 0.U
  io.toSRAM.bits.writeLen := 0.U
  io.toSRAM.bits.writeEnable := false.B
  io.toSRAM.valid := (pc =/= io.fromEXU.bits.nextPC) && io.fromEXU.valid

  sramValid := io.fromSRAM.valid
  io.fromSRAM.ready := io.toSRAM.valid
  inst := io.fromSRAM.bits.readData

  io.toIDU.bits.currentPC := pc
  io.toIDU.bits.inst := inst
}
