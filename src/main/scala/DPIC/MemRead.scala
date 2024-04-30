package taohe.dpic

import chisel3._
import chisel3.util.HasBlackBoxInline

class MemRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val readAddr = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
  })
  setInline(
    "MemRead.sv",
    s"""
       |module MemRead(
       |  input  [31:0] readAddr,
       |  output [31:0] readData
       |);
       |
       |  import "DPI-C" function int vaddr_read(
       |    int addr,
       |    int lenth,
       |    int valid
       |  );
       |
       |  
       |  assign readData = vaddr_read(readAddr, 4, 1);
       |  
       |
       |endmodule
       |
       |""".stripMargin
  )
}
