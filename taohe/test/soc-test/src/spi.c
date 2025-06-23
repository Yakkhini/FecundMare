#include <soctest.h>

SpiDeviceID current_device_id = 0;

uint8_t spi_parse_device_charlen(SpiDeviceID device_id);

void spi_device_active(SpiDeviceID device_id) {
  current_device_id = device_id;

  uint32_t *spi_base = (uint32_t *)0x10001000;
  uint8_t *spi_ctrl = (uint8_t *)(spi_base + 4);
  uint32_t *spi_divider = spi_base + 5;

  uint8_t charlen = spi_parse_device_charlen(device_id);

  outl((uintptr_t)spi_ctrl, 0x00000000); // reset
  outb((uintptr_t)spi_ctrl, charlen);    // set CHARLEN
  outb((uintptr_t)(spi_ctrl + 1),
       0B00000000);                         // LSB=0, TxNEG=0, RxNEG=0, GOBSY=0
  outl((uintptr_t)spi_divider, 0x10000000); // set divider to 10000000

  return;
}

uint32_t spi_transfer(uint32_t input, bool double_step) {
  uint32_t *spi_base = (uint32_t *)0x10001000;
  uint8_t *spi_ctrl = (uint8_t *)(spi_base + 4);
  uint8_t *spi_ss = (uint8_t *)(spi_base + 6);

  outl((uintptr_t)spi_ss, current_device_id); // set SS

  // First Transfer: send data to the slave
  while ((inb((uintptr_t)(spi_ctrl + 1)) & 0x01) == 1)
    continue;
  outl((uintptr_t)(spi_base + 1), input);
  outb((uintptr_t)(spi_ctrl + 1),
       0B00000001); // LSB=0, TxNEG=0, RxNEG=0, GOBSY=1

  if (double_step) {

    // Second Transfer: receive data from the slave
    while ((inb((uintptr_t)(spi_ctrl + 1)) & 0x01) == 1)
      continue;
    outb((uintptr_t)(spi_ctrl + 1),
         0B00000001); // LSB=0, TxNEG=0, RxNEG=0, GOBSY=1
  }

  // Read the data from spi master after second transfer is done
  while ((inb((uintptr_t)(spi_ctrl + 1)) & 0x01) == 1)
    continue;
  uint32_t data = inl((uintptr_t)(spi_base));

  outl((uintptr_t)spi_ss, 0x00000000); // reset SS to close transfer

  return data;
}

uint8_t spi_parse_device_charlen(SpiDeviceID device_id) {
  switch (device_id) {
  case SPI_FLASH_ID:
    return 0B01000000; // Flash device uses 64-bit character length
  case SPI_BITREV_ID:
    return 0B00001000; // Bitrev device uses 8-bit character length
  default:
    return 0; // Unknown device
  }
}
