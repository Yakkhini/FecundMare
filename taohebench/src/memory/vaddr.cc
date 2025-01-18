#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>

#include <VTaoHe__Dpi.h>

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
