#ifndef __CPU_DIFFTEST_H__
#define __CPU_DIFFTEST_H__

#include <common.h>

void difftest_init(char *ref_so_file, long img_size, int port);
void difftest_step(vaddr_t pc, vaddr_t npc);
void difftest_skip_ref();
void difftest_skip_ref_cancel();

enum { DIFFTEST_TO_DUT, DIFFTEST_TO_REF };
#define RISCV_GPR_TYPE MUXDEF(CONFIG_RV64, uint64_t, uint32_t)
#define RISCV_GPR_NUM MUXDEF(CONFIG_RVE, 16, 32)
#define DIFFTEST_REG_SIZE                                                      \
  (sizeof(RISCV_GPR_TYPE) * (RISCV_GPR_NUM + 1)) // GPRs + pc

#endif