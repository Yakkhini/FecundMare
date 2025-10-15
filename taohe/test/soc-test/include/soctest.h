#ifndef __SOCTEST_H__
#define __SOCTEST_H__
#include <am.h>

#include <klib-macros.h>
#include <klib.h>

static inline uint8_t inb(uintptr_t addr) { return *(volatile uint8_t *)addr; }
static inline uint16_t inw(uintptr_t addr) {
  return *(volatile uint16_t *)addr;
}
static inline uint32_t inl(uintptr_t addr) {
  return *(volatile uint32_t *)addr;
}

static inline void outb(uintptr_t addr, uint8_t data) {
  *(volatile uint8_t *)addr = data;
}
static inline void outw(uintptr_t addr, uint16_t data) {
  *(volatile uint16_t *)addr = data;
}
static inline void outl(uintptr_t addr, uint32_t data) {
  *(volatile uint32_t *)addr = data;
}

typedef enum {
  SPI_FLASH_ID = 0B00000001,
  SPI_BITREV_ID = 0B10000000,
} SPIDeviceID;

typedef enum {
  MALLOC = 0xdeadbeaf,
  FLASH = 0x30000000,
  PSRAM = 0x80000000,
  SDRAM = 0xa0000000
} MemoryRegionStart;

void spi_device_active(SPIDeviceID device_id);
uint32_t spi_transfer(uint32_t input);

void mem_test(MemoryRegionStart memory_region);
void spi_test();
void board_test();

#endif
