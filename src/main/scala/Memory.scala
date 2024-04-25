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
        |  input bit withEXU_valid,
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
        |  import "DPI-C" context function void vaddr_difftest_skip_check(int addr);
        |  import "DPI-C" context function void vaddr_difftest_skip_cancel();
        |
        |  always @(posedge clock) begin
        |    if(withEXU_writeEnable & !reset) begin
        |      vaddr_write(withEXU_address, withEXU_lenth, withEXU_writeData, {31'b0, withEXU_valid});
        |    end
        |  end
        |
        |  always_comb begin
        |    if(withEXU_valid) begin
        |      withEXU_readData = vaddr_read(withEXU_address, withEXU_lenth, {31'b0, withEXU_valid});
        |      vaddr_difftest_skip_check(withEXU_address);
        |    end else begin
        |      withEXU_readData = 0;
        |      vaddr_difftest_skip_cancel();
        |    end
        |  end
        |
        |endmodule
        |
        |""".stripMargin
  )
}
