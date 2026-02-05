#ifndef __CPU_MTRACE_H__
#define __CPU_MTRACE_H__

#include <common.h>

typedef struct {
  uint32_t awaddr;
  uint32_t awsize;
  bool awvalid;
  uint32_t wdata;
  uint32_t araddr;
  uint32_t arsize;
  uint32_t arburst;
  bool arvalid;
  uint32_t rdata;
  bool rvalid;
} AXI4Interface;

void axi4_interface_sync(core_symbol_t *cpu_symbol);
bool mmio_difftest_check();
void mtrace();
void mtrace_init();
void mtrace_finish();

#endif
