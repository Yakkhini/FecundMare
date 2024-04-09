#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>

word_t mmio_read(paddr_t addr, int len) {

  switch (addr) {
  case CONFIG_RTC_MMIO:
    timer_update();
    return npc_time.usec;
    break;
  case CONFIG_RTC_MMIO + 4:
    return npc_time.sec;
    break;
  default:
    break;
  }
  return 0;
}

void mmio_write(paddr_t addr, int len, word_t data) {
  switch (addr) {
  case CONFIG_SERIAL_MMIO:
    putchar((char)data);
    break;
  default:
    break;
  }
  return;
}

void device_init() {
  timer_init();
  return;
}