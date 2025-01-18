package taohe.dpic

import chisel3._
import chisel3.util.HasBlackBoxInline

class MemRead extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val readAddr = Input(UInt(32.W))
    val readData = Output(UInt(32.W))
    val readEnable = Input(Bool())
    val clock = Input(Clock())
  })
  setInline(
    "MemRead.sv",
    s"""
       |module MemRead(
       |  input  [31:0] readAddr,
       |  output [31:0] readData,
       |  input  readEnable,
       |  input  clock
       |);
       |
       |  import "DPI-C" function int vaddr_read(
       |    int addr,
       |    int len,
       |  );
       |
       |  reg [31:0] readDataReg;
       |  
       |  always @(posedge clock) begin
       |    if(readEnable) begin
       |      readDataReg <= vaddr_read(readAddr, 4);
       |    end
       |  end
       |
       |  assign readData = readDataReg;
       |
       |endmodule
       |
       |""".stripMargin
  )
}
