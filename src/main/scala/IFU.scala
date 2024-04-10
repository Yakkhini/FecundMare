package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

class IFU extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val pc = Input(UInt(32.W))
    val inst = Output(UInt(32.W))
  })
  setInline(
    "IFU.sv",
    """
    |module IFU(
    | input bit reset,
    | input [31:0] pc,
    | output bit [31:0] inst
    |);
    |
    | import "DPI-C" function int inst_fetch(int pc);
    |
    | always_comb begin
    |   if(reset) begin
    |     inst = 0;
    |   end else begin
    |     inst = inst_fetch(pc);
    |   end
    | end
    |endmodule
    |
    |""".stripMargin
  )
}
