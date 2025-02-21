#include <am.h>
#include <klib-macros.h>
#include <klib.h>

#define FLASH 0x30000000

int main() {

  uint32_t heap_test_size = 0x100;
  void *heap_test_start = malloc(heap_test_size);

  // word test
  uint32_t *word_start = (uint32_t *)heap_test_start;
  for (int i = 0; i < heap_test_size / sizeof(uint32_t); i++) {
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

  // half test
  uint16_t *half_start = (uint16_t *)heap_test_start;
  for (int i = 0; i < heap_test_size / sizeof(uint16_t); i++) {
    half_start[i] = 0x0E0F;
    if (*(uint8_t *)(half_start + i) != 0x0F)
      halt(1);
    if (*((uint8_t *)(half_start + i) + 1) != 0x0E)
      halt(1);
  }

  // byte test
  uint8_t *byte_start = (uint8_t *)heap_test_start;
  for (int i = 0; i < heap_test_size; i++) {
    byte_start[i] = 0xAB;
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

  uint32_t *flash_start = (uint32_t *)FLASH;
  for (int i = 0; i < 0x00010000 / sizeof(uint32_t); i++) {
    if (*(uint8_t *)(flash_start + i) != 0x0D)
      halt(1);
    if (*((uint8_t *)(flash_start + i) + 1) != 0x0C)
      halt(1);
    if (*((uint8_t *)(flash_start + i) + 2) != 0x0B)
      halt(1);
    if (*((uint8_t *)(flash_start + i) + 3) != 0x0A)
      halt(1);
    if (*(uint16_t *)(flash_start + i) != 0x0C0D)
      halt(1);
    if (*((uint16_t *)(flash_start + i) + 1) != 0x0A0B)
      halt(1);
    if (*(uint32_t *)(flash_start + i) != 0x0A0B0C0D)
      halt(1);
  }

  return 0;
}
