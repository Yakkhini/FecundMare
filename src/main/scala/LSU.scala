package taohe

import chisel3._
import chisel3.util.{switch, is}

import taohe.util.LSUBundle

class LSU extends Module {
  val io = IO(new LSUBundle)

  io.toSRAM.bits.writeEnable := false.B
  io.toSRAM.valid := false.B

  switch(io.fromEXU.valid) {
    is(true.B) {
      io.toSRAM.bits.writeEnable := io.fromEXU.bits.writeEnable
      io.toSRAM.valid := true.B
    }

    is(false.B) {
      io.toSRAM.bits.writeEnable := false.B
      io.toSRAM.valid := false.B
    }
  }

  io.toSRAM.bits.readAddr := io.fromEXU.bits.address
  io.toSRAM.bits.writeAddr := io.fromEXU.bits.address
  io.toSRAM.bits.writeData := io.fromEXU.bits.writeData
  io.toSRAM.bits.writeLen := io.fromEXU.bits.lenth
  io.toEXU.bits.readData := io.fromSRAM.bits.readData
  io.toEXU.valid := io.fromSRAM.valid
  io.fromEXU.ready := true.B
  io.fromSRAM.ready := true.B

  //   "LSU.sv",
  //   """
  //       |module LSU(
  //       |  input bit clock,
  //       |  input bit reset,
  //       |  input bit fromEXU_valid,
  //       |  output bit fromEXU_ready,
  //       |  output bit toEXU_valid,
  //       |  input bit toEXU_ready,
  //       |  input bit fromEXU_bits_writeEnable,
  //       |  input [31:0] fromEXU_bits_writeData,
  //       |  output [31:0] toEXU_bits_readData,
  //       |  input [31:0] fromEXU_bits_address,
  //       |  input int fromEXU_bits_lenth
  //       |);
  //       |
  //       |  assign fromEXU_ready = 1;
  //       |  assign toEXU_valid = 1;
  //       |
  //       |  import "DPI-C" function int vaddr_read(
  //       |    int addr,
  //       |    int lenth,
  //       |    int valid
  //       |  );
  //       |
  //       |  import "DPI-C" function void vaddr_write(
  //       |    int addr,
  //       |    int lenth,
  //       |    int data,
  //       |    int valid
  //       |  );
  //       |
  //       |  import "DPI-C" context function void vaddr_difftest_skip_check(int addr);
  //       |  import "DPI-C" context function void vaddr_difftest_skip_cancel();
  //       |  import "DPI-C" context function void mtrace_reset();
  //       |
  //       |  always @(posedge clock) begin
  //       |    if(fromEXU_bits_writeEnable & !reset & fromEXU_valid) begin
  //       |      vaddr_write(fromEXU_bits_address, fromEXU_bits_lenth, fromEXU_bits_writeData, {31'b0, fromEXU_valid});
  //       |    end
  //       |  end
  //       |
  //       |  always_comb begin
  //       |    if(fromEXU_valid) begin
  //       |      vaddr_difftest_skip_check(fromEXU_bits_address);
  //       |    end else begin
  //       |      vaddr_difftest_skip_cancel();
  //       |    end
  //       |  end
  //       |
  //       |  always_comb begin
  //       |    if(!fromEXU_bits_writeEnable & fromEXU_valid) begin
  //       |      toEXU_bits_readData = vaddr_read(fromEXU_bits_address, fromEXU_bits_lenth, {31'b0, fromEXU_valid});
  //       |    end else begin
  //       |      toEXU_bits_readData = 0;
  //       |      mtrace_reset();
  //       |    end
  //       |  end
  //       |
  //       |endmodule
  //       |
  //       |""".stripMargin
  //
}
