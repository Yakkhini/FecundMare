#ifndef __DEVICE_H__
#define __DEVICE_H__

#include <common.h>

typedef struct {
  uint32_t sec;
  uint32_t usec;
} NPCTime;

extern NPCTime npc_time;

void device_init();

void timer_init();
void timer_update();

word_t mmio_read(paddr_t addr, int len);
void mmio_write(paddr_t addr, int len, word_t data);

bool in_mmio(paddr_t addr);
#endif