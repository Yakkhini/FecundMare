#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>

#include <VysyxSoCFull__Dpi.h>

uint8_t FLASH[0x1000000] __attribute((aligned(4096))) = {};

extern "C" {

int vaddr_read(int addr, int len) {

  int ret = paddr_read(addr, len);

  return ret;
}

void vaddr_write(int addr, int len, int data) { paddr_write(addr, len, data); }
}

long flash_init(char *img_file) {
  uint32_t *flash_start = (uint32_t *)FLASH;
  while (flash_start < (uint32_t *)(FLASH + 0x1000000)) {
    *flash_start = 0x0A0B0C0D;
    flash_start++;
  }

  FILE *fp = fopen(img_file, "rb");

  fseek(fp, 0, SEEK_END);
  long size = ftell(fp);

  Log("Load program %s in flash, size = %ld", img_file, size);

  fseek(fp, 0, SEEK_SET);
  int ret = fread((void *)FLASH, size, 1, fp);
  assert(ret == 1);

  fclose(fp);

  return size;
}

extern "C" void mrom_read(int32_t addr, int32_t *data) {
  *data = vaddr_read((addr & 0xFFFFFFFC) + 0x60000000, 4);
  return;
}

extern "C" void flash_read(int32_t addr, int32_t *data) {
  *data = *(uint32_t *)(FLASH + (addr & 0xFFFFFFFC));

#if CONFIG_MTRACE_FLASH
  Log("flash_read: addr = 0x%08x, data = 0x%08x", addr + 0x30000000, *data);
#endif

  return;
}
