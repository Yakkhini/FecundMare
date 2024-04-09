#include "VTaoHe.h"
#include "VTaoHe__Syms.h"
#include "sdb.h"
#include <common.h>
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cpu/disasm.h>
#include <cpu/ftrace.h>
#include <memory/vaddr.h>

CPU cpu;
NPCState npc_state = SRIZ_INIT;

static char *NPC_HOME = getenv("NPC_HOME");
static VerilatedContext *contextp;

#if CONFIG_WAVE_RECORD
static VerilatedVcdC *tfp;
#endif

void finish();
void halt(int code) {
  if (npc_state == SRIZ_RUNNING || npc_state == SRIZ_PAUSE) {
    switch (code) {
    case 0:
      npc_state = SRIZ_HALT;
      break;
    default:
      npc_state = SRIZ_ABORT;
      break;
    }
  }
}

int return_status() {
  switch (npc_state) {
  case SRIZ_HALT:
    return 0;
  case SRIZ_ABORT:
    return 1;
  default:
    return 2;
  }
}

void cpu_sync();
void cpu_check();
void single_clock() {
#if CONFIG_WAVE_RECORD
  contextp->timeInc(1);
#endif
  cpu.top->clock = 1;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
  contextp->timeInc(1);
#endif

  cpu.top->clock = 0;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
#endif

  cpu_sync();
  cpu_check();
}

void reset() {
#if CONFIG_WAVE_RECORD
  contextp->timeInc(1);
#endif

  cpu.top->reset = 1;
  cpu.top->clock = 1;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
  contextp->timeInc(1);
#endif

  cpu.top->clock = 0;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
  contextp->timeInc(1);
#endif

  cpu.top->clock = 1;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
  contextp->timeInc(1);
#endif

  cpu.top->reset = 0;
  cpu.top->clock = 0;
  cpu.top->eval();

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
#endif

  cpu_sync();
  cpu_check();

  Log("SRIZ reset done.");
}

void cpu_init(int argc, char **argv) {
#if CONFIG_DISASM
  disasm_init();
#endif

  contextp = new VerilatedContext;
  contextp->commandArgs(argc, argv);

  cpu.top = new VTaoHe(contextp);
  Log("Welcome to SuanChou Processor Core Verilating Model.");

#if CONFIG_WAVE_RECORD
  char wavefile_name[80];
  strcpy(wavefile_name, NPC_HOME);
  strcat(wavefile_name, "/build/waveform.vcd");
  Log("Wave Path: %s.", wavefile_name);

  Verilated::traceEverOn(true);
  tfp = new VerilatedVcdC;
  cpu.top->trace(tfp, 5);

  tfp->open(wavefile_name);
#else
  Log("Waveform Recording: " ANSI_FMT("DISABLE", ANSI_FG_GREEN) ANSI_FG_BLUE);
#endif
}

void cpu_exec(int n) {
  switch (npc_state) {
  case SRIZ_INIT:
    reset();
    npc_state = SRIZ_RUNNING;
    break;
  case SRIZ_HALT:
    Log("Program already finished!");
    return;
  case SRIZ_ABORT:
    Log("Program already aborted!");
    return;
  default:
    break;
  }

  npc_state = SRIZ_RUNNING;

  switch (n) {
  case -1:
    while (npc_state == SRIZ_RUNNING) {
      single_clock();
    }
    break;
  default:
    for (int i = 0; i < n; i++) {
      if (npc_state != SRIZ_RUNNING) {
        break;
      }
      single_clock();
    }
    break;
  }
}

void cpu_sync() {
  // memcpy(cpu.regs, cpu.top->sriz->resgister_file->rf.m_storage, 32 * 4);
  // if (cpu.top->sriz->resgister_file->wen) {
  //   cpu.regs[cpu.top->sriz->resgister_file->waddr] =
  //       cpu.top->sriz->resgister_file->regin;
  // }
  // cpu.pc_prev = cpu.pc;
  // cpu.pc = cpu.top->sriz->pc_reg->pcin;
  // cpu.inst = cpu.top->sriz->inst;
}

void cpu_check() {

#if CONFIG_DISASM
  disassembler();
#endif

  // if (cpu.top->sriz->IDU->lut->hit == 0) {
  //   Log("ERROR INST NOT SUPPORT: LUT HIT FAILED at pc = 0x%08X",
  //   cpu.pc_prev); npc_state = SRIZ_ABORT;
  // }

#if CONFIG_FTRACE
  ftrace_check();
#endif

#if CONFIG_MTRACE
  mtrace();
#endif

#if CONFIG_DIFFTEST
  difftest_step(cpu.pc_prev, cpu.pc);
#endif

#if CONFIG_WATCHPOINT
  check_wp();
#endif

  finish();
}

int inst_fetch(int pc) { return vaddr_ifetch(pc); }

void finish() {
  switch (npc_state) {
  case SRIZ_ABORT:
    Log("SRIZ: " ANSI_FMT("ABORT", ANSI_FG_RED) ANSI_FG_BLUE
        " at pc = 0x%08X " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED),
        cpu.pc);
    return;
  case SRIZ_HALT:
    Log("SRIZ: " ANSI_FMT("QUIT", ANSI_FG_GREEN) ANSI_FG_BLUE
        " at pc = 0x%08X " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN),
        cpu.pc);
    return;
  default:
    return;
  }
}

void cpu_exit() {
#if CONFIG_WAVE_RECORD
  tfp->close();
#endif

  cpu.top->final();
  delete cpu.top;

  disasm_exit();
}