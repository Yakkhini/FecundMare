#include <capstone/capstone.h>
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/disasm.h>

static csh hadler;

void disasm_init() { cs_open(CS_ARCH_RISCV, CS_MODE_RISCV32, &hadler); }

void disassembler() {
  cs_insn *insn;
  int count =
      cs_disasm(hadler, (uint8_t *)&cpu.inst, sizeof(word_t), 0, 1, &insn);

  uint8_t byte1 = (cpu.inst >> 24) & 0xFF;
  uint8_t byte2 = (cpu.inst >> 16) & 0xFF;
  uint8_t byte3 = (cpu.inst >> 8) & 0xFF;
  uint8_t byte4 = cpu.inst & 0xFF;

  if (count) {
    printf("[0x%08X]: %02x %02x %02x %02x %s\t%s\n", cpu.pc_prev, byte1, byte2,
           byte3, byte4, insn->mnemonic, insn->op_str);
    free(insn);
  } else {
    Log("pc at 0x%08X: " ANSI_FMT("INST INVALID ERROR", ANSI_FG_RED),
        cpu.pc_prev);
    halt(1);
  }
}

void disasm_exit() { cs_close(&hadler); }