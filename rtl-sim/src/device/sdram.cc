#include <common.h>
#include <device/device.h>

typedef uint16_t sdram_row_t[512];

typedef struct SDRAMBank {
  uint32_t col_size = 512;
  uint32_t row_size = 8192;
  uint32_t active_line_index = 0;
  sdram_row_t active_line;
  sdram_row_t *memory;
} SDRAMBank;

SDRAMBank sdram_banks[16];

void sdram_init() {
  for (int i = 0; i < 16; i++) {
    sdram_banks[i].memory =
        (sdram_row_t *)malloc(sizeof(sdram_row_t) * sdram_banks[i].row_size);
    memset(sdram_banks[i].memory, 0,
           sizeof(sdram_row_t) * sdram_banks[i].row_size);
  }
}

void sdram_active_line(int32_t index, int32_t row_address) {
  assert(index >= 0 && index < 16);
  assert(row_address >= 0 && row_address < sdram_banks[index].row_size);
  memcpy(sdram_banks[index].active_line, sdram_banks[index].memory[row_address],
         sizeof(sdram_row_t));
  sdram_banks[index].active_line_index = row_address;
}

void sdram_deactive_line(int32_t index) {
  assert(index >= 0 && index < 16);
  memcpy(sdram_banks[index].memory[sdram_banks[index].active_line_index],
         sdram_banks[index].active_line, sizeof(sdram_row_t));
}

void sdram_read_data(int32_t index, int32_t col_address, int16_t *read_data) {
  assert(index >= 0 && index < 16);
  assert(col_address >= 0 && col_address < sdram_banks[index].col_size);
  *read_data = sdram_banks[index].active_line[col_address];
}

void sdram_write_data(int32_t index, int32_t col_address, int16_t input_data) {
  assert(index >= 0 && index < 16);
  assert(col_address >= 0 && col_address < sdram_banks[index].col_size);
  sdram_banks[index].active_line[col_address] = input_data;
}
