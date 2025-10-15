#include "soctest.h"

MemoryRegionStart memory_region;

int main(const char *args) {

  switch (args[0]) {
  case '3':
    memory_region = FLASH;
    break;
  case '8':
    memory_region = PSRAM;
    break;
  case 'a':
    memory_region = SDRAM;
    break;
  case 'n':
    board_test();
    return 0;
  default:
    memory_region = MALLOC;
  }

  mem_test(memory_region);

  return 0;
}
