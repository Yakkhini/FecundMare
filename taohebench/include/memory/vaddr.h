#ifndef __MEMORY_VADDR_H__
#define __MEMORY_VADDR_H__

#include <common.h>

#define FLASH_BASE ((paddr_t)0x30000000)
long flash_init(char *img_file);

extern uint8_t FLASH[];
extern "C" void flash_read(int32_t addr, int32_t *data);

static inline bool in_flash(paddr_t addr) {
  return addr - FLASH_BASE < 0x1000000;
}

#endif
