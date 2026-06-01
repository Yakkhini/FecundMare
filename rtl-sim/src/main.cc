#include <common.h>
#include <cpu/cpu.h>
#include <device/device.h>
#include <monitor.h>
#include <nvboard.h>
#include <sdb.h>

int main(int argc, char **argv) {

  monitor_init(argc, argv);
  cpu_init(argc, argv);

  device_init();

#if CONFIG_NVBOARD
  nvboard_bind_all_pins();
  nvboard_init();
#endif

  sdb_mainloop();

  cpu_exit();

  return return_status();
}
