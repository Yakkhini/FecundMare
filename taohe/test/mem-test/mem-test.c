#include <am.h>
#include <klib-macros.h>
#include <klib.h>


int main() {

  // word test
  uint32_t *word_start = (uint32_t *)STACK_LEFT;
  for (int i = 0; i < STACK_SIZE / sizeof(uint32_t); i++) {
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

  return 0;
}
