#include "soctest.h"
#include <am.h>
#include <klib-macros.h>
#include <klib.h>

void spi_test_case(uint32_t input, uint32_t expect);

// Access Bitrev device which is a slave connect
// to the ysyxSoC's SPI master.
void spi_test() {
  printf("SPI Test Start\n");

  uint32_t *spi_base = (uint32_t *)0x10001000;
  uint8_t *spi_ctrl = (uint8_t *)(spi_base + 4);
  uint32_t *spi_divider = spi_base + 5;
  uint8_t *spi_ss = (uint8_t *)(spi_base + 6);

  outl((uintptr_t)(spi_base + 4), 0x00000000); // reset
  outb((uintptr_t)spi_ctrl, 0B00001000);       // set CHARLEN to 8
  outb((uintptr_t)(spi_ctrl + 1),
       0B00000010); // LSB=0, TxNEG=0, RxNEG=1, GOBSY=0

  outl((uintptr_t)spi_divider, 0x10000000); // set divider to 10000000
  outl((uintptr_t)spi_ss, 0B10000000);      // set SS to seven bit

  spi_test_case(0x0F, 0xF0);
  spi_test_case(0xF0, 0x0F);
  spi_test_case(0B00000001, 0B10000000);
  spi_test_case(0B10000000, 0B00000001);
  spi_test_case(0B10101010, 0B01010101);
  spi_test_case(0B01010101, 0B10101010);
}

void spi_test_case(uint32_t input, uint32_t expect) {
  uint32_t *spi_base = (uint32_t *)0x10001000;
  uint8_t *spi_ctrl = (uint8_t *)(spi_base + 4);

  // First Transfer: send data to the slave
  while ((inb((uintptr_t)(spi_ctrl + 1)) & 0B00000001) == 1)
    ;
  outl((uintptr_t)spi_base, input); // send 0x00
  outb((uintptr_t)(spi_ctrl + 1),
       0B00000011); // LSB=0, TxNEG=0, RxNEG=1, GOBSY=1

  // Second Transfer: receive data from the slave
  while ((inb((uintptr_t)(spi_ctrl + 1)) & 0B00000001) == 1)
    ;
  outb((uintptr_t)(spi_ctrl + 1),
       0B00000011); // LSB=0, TxNEG=0, RxNEG=1, GOBSY=1

  // Read the data from spi master after second transfer is done
  while ((inb((uintptr_t)(spi_ctrl + 1)) & 0B00000001) == 1)
    ;
  uint32_t data = inw((uintptr_t)spi_base);

  // Assert the data is correct
  assert(data == expect);
  printf("SPI Test: 0x%02X -> 0x%02X\n", input, data);
}
