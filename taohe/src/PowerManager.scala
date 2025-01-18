package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

class PowerManager extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val breakSignal = Input(Bool())
    val code = Input(UInt(32.W))
  })
  setInline(
    "PowerManager.sv",
    """
    |module PowerManager(
    |  input bit reset,
    |  input bit breakSignal,
    |  input [31:0] code
    |);
    |
    |  import "DPI-C" function void halt(int code);
    |
    |  always_comb begin
    |    if(breakSignal & !reset) begin
    |      halt(code);
    |    end
    |  end
    |endmodule
    |
    |""".stripMargin
  )
}
