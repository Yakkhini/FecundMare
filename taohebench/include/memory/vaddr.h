#ifndef __MEMORY_VADDR_H__
#define __MEMORY_VADDR_H__

#include <common.h>

#define FLASH_BASE ((paddr_t)0x30000000)
long flash_init(char *img_file);

extern uint8_t FLASH[];

#endif
