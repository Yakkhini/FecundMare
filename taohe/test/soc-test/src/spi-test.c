#include <am.h>
#include <klib-macros.h>
#include <klib.h>

#include <soctest.h>

void spi_test_case(uint32_t input, uint32_t expect);

// Access Bitrev device which is a slave connect
// to the ysyxSoC's SPI master.
void spi_test() {
  printf("SPI Test Start\n");

  spi_device_active(SPI_BITREV_ID);

  spi_test_case(0x0F, 0xF0);
  spi_test_case(0xF0, 0x0F);
  spi_test_case(0B00000001, 0B10000000);
  spi_test_case(0B10000000, 0B00000001);
  spi_test_case(0B10101010, 0B01010101);
  spi_test_case(0B01010101, 0B10101010);
}

void spi_test_case(uint32_t input, uint32_t expect) {

  uint32_t data;
  data = spi_transfer(input);

  // Assert the data is correct
  assert(data == expect);
  printf("SPI Test: 0x%02X -> 0x%02X\n", input, data);
}
