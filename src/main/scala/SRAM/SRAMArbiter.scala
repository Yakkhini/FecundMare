package taohe

import chisel3._
import taohe.util.SRAMBundle
import chisel3.util.{switch, is}

class SRAMArbiter extends Module {
  val ifuIO = IO(new SRAMBundle)
  val lsuIO = IO(new SRAMBundle)

  val sram1 = Module(new SRAM)
  val sram2 = Module(new SRAM)

  sram1.io <> ifuIO
  sram2.io <> lsuIO
}
