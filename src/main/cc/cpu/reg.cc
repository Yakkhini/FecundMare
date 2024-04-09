#include <common.h>
#include <cpu/cpu.h>
#include <cstdio>

const char *regs_name[] = {"$0", "ra", "sp",  "gp",  "tp", "t0", "t1", "t2",
                           "s0", "s1", "a0",  "a1",  "a2", "a3", "a4", "a5",
                           "a6", "a7", "s2",  "s3",  "s4", "s5", "s6", "s7",
                           "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"};

void isa_reg_display() {
  printf("\tpc 0x%X\n", cpu.pc);
  printf("\tPrint regsisters...\n");
  printf("\tzero 0x%X;\n", cpu.regs[0]);
  printf("\t%s 0x%X;   ", regs_name[1], cpu.regs[1]);
  printf("\t%s 0x%X;   ", regs_name[2], cpu.regs[2]);
  printf("\t%s 0x%X;\n", regs_name[3], cpu.regs[3]);

  for (int i = 4; i < 29; i += 4) {
    printf("\t%s 0x%X;   ", regs_name[i], cpu.regs[i]);
    printf("\t%s 0x%X;   ", regs_name[i + 1], cpu.regs[i + 1]);
    printf("\t%s 0x%X;   ", regs_name[i + 2], cpu.regs[i + 2]);
    printf("\t%s 0x%X;\n", regs_name[i + 3], cpu.regs[i + 3]);
  }
}

word_t isa_reg_str2val(const char *s, bool *success) {
  if ((strcmp(s, "$0") == 0) || strcmp(s, "$zero") == 0) {
    *success = true;
    return 0;
  }
  s += 1;
  for (int i = 1; i < 30; i++) {
    if (strcmp(s, regs_name[i]) == 0) {
      *success = true;
      return cpu.regs[i];
    }
  }
  return -1;
}
