#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cpu/mtrace.h>
#include <device/device.h>
#include <memory/vaddr.h>

AXI4Interface axi4_interface;

void axi4_interface_sync(core_symbol_t *cpu_symbol) {
  axi4_interface.awaddr = cpu_symbol->io_master_awaddr;
  axi4_interface.awsize = cpu_symbol->io_master_awsize;
  axi4_interface.awvalid = cpu_symbol->io_master_awvalid;
  axi4_interface.wdata = cpu_symbol->io_master_wdata;

  axi4_interface.araddr = cpu_symbol->io_master_araddr;
  axi4_interface.arsize = cpu_symbol->io_master_arsize;
  axi4_interface.arvalid = cpu_symbol->io_master_arvalid;
  axi4_interface.rdata = cpu_symbol->io_master_rdata;
  axi4_interface.rvalid = cpu_symbol->io_master_rvalid;

#ifdef CONFIG_TARGET_TaoHe
  cpu.top->io_master_awready = 1;
  cpu.top->io_master_wready = 1;
  cpu.top->io_master_arready = 1;
#endif
}

bool in_mmio(uint32_t addr) {
  if (addr >= 0x10000000 && addr < 0x10001000) {
    return true;
  }

  if (addr >= 0x02000000 && addr < 0x02000008) {
    printf("Skipping RTC read at addr = 0x%08x\n", addr);
    return true;
  }

  return false;
}

void mtrace() {
  if (axi4_interface.awvalid) {
#if CONFIG_MTRACE
    Log("AXI4 Write addr = 0x%08x, size = %b, data = 0x%08x at inst count "
        "0x%08x",
        axi4_interface.awaddr, axi4_interface.awsize, axi4_interface.wdata,
        cpu.iCount);
#endif

    uint32_t wdata =
        axi4_interface.wdata >> ((axi4_interface.awaddr & 0b11) * 8);
    if (in_mmio(axi4_interface.awaddr)) {
      mmio_write(axi4_interface.awaddr, 0b00, wdata);
      difftest_skip_ref();
    } else {
#ifdef CONFIG_TARGET_TaoHe
      switch (axi4_interface.awsize) {
      case 0b000:
        *(uint8_t *)(&FLASH[axi4_interface.awaddr - 0x30000000]) =
            (uint8_t)(wdata & 0xFF);
        break;
      case 0b001:
        *(uint16_t *)(&FLASH[axi4_interface.awaddr - 0x30000000]) =
            (uint16_t)(wdata & 0xFFFF);
        break;
      case 0b010:
        *(uint32_t *)(&FLASH[axi4_interface.awaddr - 0x30000000]) = wdata;
        break;
      }
      cpu.top->io_master_bvalid = 1;
#endif
    }
  }

  if (axi4_interface.arvalid) {
#if CONFIG_MTRACE
    Log("AXI4 Read addr = 0x%08x, size = %b at inst count 0x%08x",
        axi4_interface.araddr, axi4_interface.arsize, cpu.iCount);
#endif
    if (in_mmio(axi4_interface.araddr)) {
      difftest_skip_ref();
    } else {
#ifdef CONFIG_TARGET_TaoHe
      uint32_t index = (axi4_interface.araddr & 0xFFFFFFFC) - 0x30000000;
      uint32_t rdata = *(uint32_t *)(&FLASH[index]);
      cpu.top->io_master_rdata = rdata;
      cpu.top->io_master_rvalid = 1;
#endif
    }
  }

#if CONFIG_MTRACE
  if (axi4_interface.rvalid) {
    Log("AXI4 Read Data: data = 0x%08x", axi4_interface.rdata);
  }
#endif
}
