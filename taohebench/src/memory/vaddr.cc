#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>

#include <VysyxSoCFull__Dpi.h>

static uint8_t FLASH[0x10000000] __attribute((aligned(4096))) = {};

extern "C" {

int vaddr_read(int addr, int len) {

  int ret = paddr_read(addr, len);

#if CONFIG_MTRACE
  Log("[MTRACE] vaddr_read: addr = 0x%x, len = %d, data = 0x%x", addr, len,
      ret);
#endif

  return ret;
}

void vaddr_write(int addr, int len, int data) {

#if CONFIG_MTRACE
  Log("[MTRACE] vaddr_write: addr = 0x%x, len = %d, data = 0x%x", addr, len,
      data);
#endif
  paddr_write(addr, len, data);
}
}

extern "C" void mrom_read(int32_t addr, int32_t *data) {
  *data = vaddr_read((addr & 0xFFFFFFFC) + 0x60000000, 4);
  return;
}

extern "C" void flash_read(int32_t addr, int32_t *data) {
  // *data = vaddr_read((addr & 0xFFFFFFFC) + 0x50000000, 4);
  *data = *(uint32_t *)(FLASH + (addr & 0xFFFFFFFC) - 0x30000000);
  return;
}
