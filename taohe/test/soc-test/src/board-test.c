#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <soctest.h>

AM_SOC_LEDS_T leds;
uint32_t last_time = 0;

void light_step() {
  if (last_time == 0) {
    last_time = 20;
    ioe_write(AM_SOC_LEDS, &leds);
    leds.value = (leds.value << 1) | (leds.value >> 15);
  } else {
    last_time--;
  }

  return;
}

void board_test() {
  printf("Board test start!\n");
  printf("Set switch to 0x8a5b to running led, "
         "0x1234 to show id, "
         "0x9c31 to quit\n");

  leds.value = 1;
  last_time = 20;

  AM_SOC_SWITCHES_T switches;
  AM_SOC_7SEGS_T segs;

  while (true) {
    ioe_read(AM_SOC_SWITCHES, &switches);

    segs.value = switches.value == 0x1234 ? 0x23060042 : switches.value;
    ioe_write(AM_SOC_7SEGS, &segs);

    if (switches.value == 0x8a5b) {
      light_step();
    } else {
      leds.value = switches.value;
      ioe_write(AM_SOC_LEDS, &leds);
      leds.value = 1;
    }

    if (switches.value == 0x9c31) {
      break;
    }
  }

  return;
}
