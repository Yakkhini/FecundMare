package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

import taohe.util.IFUBundle

class IFU extends Module {
  val io = IO(new IFUBundle)

  val pc = RegInit("h80000000".U(32.W))
  val inst = Wire(UInt(32.W))
}
