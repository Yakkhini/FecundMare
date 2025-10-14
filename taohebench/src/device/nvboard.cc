#include <common.h>
#include <cpu/cpu.h>
#include <device/device.h>
#include <nvboard.h>

#include <VysyxSoCFull.h>

void nvboard_bind_all_pins() {
  nvboard_bind_pin(&cpu.top->externalPins_gpio_out, 16, LD15, LD14, LD13, LD12,
                   LD11, LD10, LD9, LD8, LD7, LD6, LD5, LD4, LD3, LD2, LD1,
                   LD0);
  nvboard_bind_pin(&cpu.top->externalPins_gpio_in, 16, SW15, SW14, SW13, SW12,
                   SW11, SW10, SW9, SW8, SW7, SW6, SW5, SW4, SW3, SW2, SW1,
                   SW0);
  nvboard_bind_pin(&cpu.top->externalPins_gpio_seg_0, 8, SEG0A, SEG0B, SEG0C,
                   SEG0D, SEG0E, SEG0F, SEG0G, DEC0P);
  nvboard_bind_pin(&cpu.top->externalPins_gpio_seg_1, 8, SEG1A, SEG1B, SEG1C,
                   SEG1D, SEG1E, SEG1F, SEG1G, DEC1P);
  nvboard_bind_pin(&cpu.top->externalPins_gpio_seg_2, 8, SEG2A, SEG2B, SEG2C,
                   SEG2D, SEG2E, SEG2F, SEG2G, DEC2P);
  nvboard_bind_pin(&cpu.top->externalPins_gpio_seg_3, 8, SEG3A, SEG3B, SEG3C,
                   SEG3D, SEG3E, SEG3F, SEG3G, DEC3P);
}
