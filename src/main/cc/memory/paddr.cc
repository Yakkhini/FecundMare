#include "cpu/cpu.h"
#include <common.h>
#include <cpu/difftest.h>
#include <device/device.h>
#include <memory/host.h>
#include <memory/paddr.h>

static uint8_t MEM[MSISE] __attribute((aligned(4096))) = {};

uint8_t *guest_to_host(paddr_t paddr) { return MEM + paddr - MBASE; }
paddr_t host_to_guest(uint8_t *haddr) { return haddr - MEM + MBASE; }

static word_t pmem_read(paddr_t addr, int len) {
  word_t ret = host_read(guest_to_host(addr), len);
  return ret;
}

static void pmem_write(paddr_t addr, int len, word_t data) {
  host_write(guest_to_host(addr), len, data);
}

word_t paddr_ifetch(paddr_t addr) {
  if (in_pmem(addr)) {
    return pmem_read(addr, 4);
  }

  Log(ANSI_FG_RED "Invalid instruction fetch at address 0x%08x", addr);
  halt(1);

  return 0x80000000;
}

word_t paddr_read(paddr_t addr, int len) {
  if (in_pmem(addr)) {
    return pmem_read(addr, len);
  }

#if CONFIG_DEVICE
  return mmio_read(addr, len);
#endif

  Log("Invalid memory access at address 0x%08x", addr);
  return 0;
}

void paddr_write(paddr_t addr, int len, word_t data) {
  if (in_pmem(addr)) {
    pmem_write(addr, len, data);
    return;
  }

#if CONFIG_DEVICE
  mmio_write(addr, len, data);
  return;
#endif

  Log("Invalid memory access at address 0x%08x", addr);
}
