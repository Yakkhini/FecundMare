#include "soctest.h"
#include <am.h>
#include <klib-macros.h>
#include <klib.h>

uint32_t flash_spi_read(uint32_t addr);
void mem_test(MemoryRegionStart memory_region) {

  uint32_t mem_test_size = 0x100000;

  void *mem_test_start = (void *)memory_region;

  if (memory_region == MALLOC) {
    mem_test_start = malloc(memory_region);
  }

  printf("Memory test start at address 0x%08x\n", (uint32_t)mem_test_start);

  // word test
  uint32_t *word_start = (uint32_t *)mem_test_start;
  for (int i = 0; i < mem_test_size / sizeof(uint32_t); i++) {
    word_start[i] = 0x0A0B0C0D;
    if (*(uint8_t *)(word_start + i) != 0x0D)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 1) != 0x0C)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 2) != 0x0B)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 3) != 0x0A)
      halt(1);
  }

  for (int i = 0; i < mem_test_size / sizeof(uint32_t); i++) {
    if (*(uint8_t *)(word_start + i) != 0x0D)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 1) != 0x0C)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 2) != 0x0B)
      halt(1);
    if (*((uint8_t *)(word_start + i) + 3) != 0x0A)
      halt(1);
  }

  // half test
  uint16_t *half_start = (uint16_t *)mem_test_start;
  for (int i = 0; i < mem_test_size / sizeof(uint16_t); i++) {
    half_start[i] = 0x0E0F;
    if (*(uint8_t *)(half_start + i) != 0x0F)
      halt(1);
    if (*((uint8_t *)(half_start + i) + 1) != 0x0E)
      halt(1);
  }

  // half test
  for (int i = 0; i < mem_test_size / sizeof(uint16_t); i++) {
    if (*(uint8_t *)(half_start + i) != 0x0F)
      halt(1);
    if (*((uint8_t *)(half_start + i) + 1) != 0x0E)
      halt(1);
  }

  // byte test
  uint8_t *byte_start = (uint8_t *)mem_test_start;
  for (int i = 0; i < mem_test_size; i++) {
    byte_start[i] = 0xAB;
    if (byte_start[i] != 0xAB)
      halt(1);
  }

  for (int i = 0; i < mem_test_size; i++) {
    if (byte_start[i] != 0xAB)
      halt(1);
  }

  // Flash test
  //
  // ref: code snippet from vaddr.cc in test environment
  // void flash_init() {
  //   uint32_t *flash_start = (uint32_t *)FLASH;
  //   while (flash_start < (uint32_t *)(FLASH + 0x10000000)) {
  //     *flash_start = 0x0A0B0C0D;
  //     flash_start++;
  //   }
  // }
  //
  // todo: currently the flash not store program, which means
  // it not be modified after initialization.

  // spi_device_active(SPI_FLASH_ID);
  // uint32_t flash_program_size = 0;
  //
  // uint32_t flash_start = FLASH;
  // for (int i = 0; i < 0x00001000 / sizeof(uint32_t); i++) {
  //   if (flash_spi_read(flash_start + i) == 0x0A0B0C0D) {
  //     printf("Program end at address 0x%08x\n", flash_start + i);
  //     flash_program_size = i * sizeof(uint32_t);
  //     break;
  //   }
  // }
  //
  // void *program_dest = malloc(flash_program_size);
  // for (int i = 0; i < flash_program_size / sizeof(uint32_t); i++) {
  //   ((uint32_t *)program_dest)[i] =
  //       flash_spi_read(flash_start + i * sizeof(uint32_t));
  // }
  //
  // Currently the program's UART access is a simple implementation which do not
  // wait the finish signal and cause many outputs rather than four.
  //
  // This is an acceptable behavior because it's a embedded program without
  // libraries and exceptions.
  // ((void (*)())program_dest)();

  // The program is loaded to the XIP memory, and we can execute it directly.
  // void *program_xip = (void *)FLASH;
  // ((void (*)())program_xip)();

  printf("Memory test passed!\n");
}

uint32_t flash_spi_read(uint32_t addr) {
  // Input struct: [31:24] 0x03 (read instuction from spec), [23:0] addr
  uint32_t input, raw_bigendian_data, result;
  input = 0x03000000 | (addr & 0x00FFFFFF);
  raw_bigendian_data = spi_transfer(input);
  result = ((raw_bigendian_data & 0x000000FF) << 24) |
           ((raw_bigendian_data & 0x0000FF00) << 8) |
           ((raw_bigendian_data & 0x00FF0000) >> 8) |
           ((raw_bigendian_data & 0xFF000000) >> 24);

  return result;
}
