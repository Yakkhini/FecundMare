#include <common.h>
#include <cpu/cpu.h>
#include <device/device.h>
#include <monitor.h>
#include <sdb.h>

extern "C" void flash_read(int32_t addr, int32_t *data) { assert(0); }
extern "C" void mrom_read(int32_t addr, int32_t *data) {
  *data = 0x00000173;
  return;
}

int main(int argc, char **argv) {

  monitor_init(argc, argv);
  cpu_init(argc, argv);
  device_init();

  sdb_mainloop();

  cpu_exit();

  return return_status();
}
