package taohe

import chisel3._
import taohe.util.AXI4LiteBundle
import chisel3.util.Arbiter
import chisel3.util.{switch, is}

class SRAMArbiter extends Module {
  val ifuIO = IO(Flipped(new AXI4LiteBundle))
  val lsuIO = IO(Flipped(new AXI4LiteBundle))

  val sram1 = Module(new SRAM)
  val sram2 = Module(new SRAM)

  sram1.io <> ifuIO
  sram2.io <> lsuIO
}
