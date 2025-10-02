#include <cpu/cpu.h>
#include <cpu/mtrace.h>

#include <VysyxSoCFull.h>
#include <VysyxSoCFull__Dpi.h>
#include <VysyxSoCFull__Syms.h>

AXI4Interface axi4_interface;

void axi4_interface_sync() {
  axi4_interface.awaddr =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_awaddr;
  axi4_interface.awsize =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_awsize;
  axi4_interface.awvalid =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_awvalid;
  axi4_interface.wdata = cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_wdata;

  axi4_interface.araddr =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_araddr;
  axi4_interface.arsize =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_arsize;
  axi4_interface.arvalid =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_arvalid;
  axi4_interface.rdata = cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_rdata;
  axi4_interface.rvalid =
      cpu.top->ysyxSoCFull->asic->cpu->cpu->io_master_rvalid;
}

void mtrace() {
  if (axi4_interface.awvalid) {
    Log("AXI4 Write: addr = 0x%08x, size = %b, data = 0x%08x",
        axi4_interface.awaddr, axi4_interface.awsize, axi4_interface.wdata);
  }

  if (axi4_interface.arvalid) {
    Log("AXI4 Read: addr = 0x%08x, size = %b", axi4_interface.araddr,
        axi4_interface.arsize);
  }

  if (axi4_interface.rvalid) {
    Log("AXI4 Read Data: data = 0x%08x", axi4_interface.rdata);
  }
}
