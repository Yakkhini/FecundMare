package taohe

import chisel3._
import chisel3.util.HasBlackBoxInline

import taohe.util.MemoryBundle

class Memory extends BlackBox with HasBlackBoxInline {
  val io = IO(new MemoryBundle)
  setInline(
    "Memory.sv",
    """
        |module Memory(
        |  input bit clock,
        |  input bit reset,
        |  input bit valid,
        |  input bit withEXU_writeEnable,
        |  input [31:0] withEXU_writeData,
        |  output [31:0] withEXU_readData,
        |  input [31:0] withEXU_address,
        |  input int withEXU_lenth
        |);
        |
        |  import "DPI-C" function int vaddr_read(
        |    int addr,
        |    int lenth,
        |    int valid
        |  );
        |
        |  import "DPI-C" function void vaddr_write(
        |    int addr,
        |    int lenth,
        |    int data,
        |    int valid
        |  );
        |
        |  always @(posedge clock) begin
        |    if(withEXU_writeEnable & !reset) begin
        |      vaddr_write(withEXU_address, withEXU_lenth, withEXU_writeData, {31'b0, valid});
        |    end
        |  end
        |
        |  always_comb begin
        |    withEXU_readData = vaddr_read(withEXU_address, withEXU_lenth, {31'b0, valid});
        |  end
        |
        |endmodule
        |
        |""".stripMargin
  )
}
