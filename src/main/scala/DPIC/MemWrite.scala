package taohe.dpic

import chisel3._
import chisel3.util.HasBlackBoxInline

import taohe.util.enum._

class MemWrite extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val writeAddr = Input(UInt(32.W))
    val writeData = Input(UInt(32.W))
    val writeLen = Input(UInt(32.W))
    val writeEnable = Input(Bool())
    val clock = Input(Clock())
  })
  setInline(
    "MemWrite.sv",
    s"""
       |module MemWrite(
       |  input  [31:0] writeAddr,
       |  input  [31:0] writeData,
       |  input  int writeLen,
       |  input  writeEnable,
       |  input  clock
       |);
       |
       |  import "DPI-C" function void vaddr_write(
       |    int addr,
       |    int len,
       |    int data,
       |  );
       |  
       |  always @(posedge clock) begin
       |    if(writeEnable) begin
       |      vaddr_write(writeAddr, writeLen, writeData);
       |    end
       |  end
       |
       |endmodule
       |
       |""".stripMargin
  )
}
