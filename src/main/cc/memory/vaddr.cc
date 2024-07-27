#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>

#include <VTaoHe__Dpi.h>

extern "C" {

int vaddr_read(int addr, int len) {

  int ret = paddr_read(addr, len);

  return ret;
}

void vaddr_write(int addr, int len, int data) { paddr_write(addr, len, data); }
}
