#include <common.h>
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cpu/disasm.h>
#include <cpu/ftrace.h>
#include <memory/vaddr.h>
#include <sdb.h>

#include <VTaoHe.h>
#include <VTaoHe__Dpi.h>
#include <VTaoHe__Syms.h>

CPU cpu;
NPCState npc_state = TCHE_INIT;

static char *NPC_CHISEL = getenv("NPC_CHISEL");
static VerilatedContext *contextp;

#if CONFIG_WAVE_RECORD
static VerilatedVcdC *tfp;
#endif

void finish();
void halt(int code) {
  if (npc_state == TCHE_RUNNING || npc_state == TCHE_PAUSE) {
    switch (code) {
    case 0:
      npc_state = TCHE_HALT;
      break;
    default:
      npc_state = TCHE_ABORT;
      break;
    }
  }
}

int return_status() {
  switch (npc_state) {
  case TCHE_HALT:
    return 0;
  case TCHE_ABORT:
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
  if (cpu.check_cycle) {
    cpu_check();
    cpu.check_cycle = false;
  }
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

  if (cpu.check_cycle) {
    cpu_check();
    cpu.check_cycle = false;
  }

  Log("TCHE reset done.");
}

void cpu_init(int argc, char **argv) {
#if CONFIG_DISASM
  disasm_init();
#endif

  contextp = new VerilatedContext;
  contextp->commandArgs(argc, argv);

  cpu.top = new VTaoHe(contextp);
  cpu.iCount = 0;
  cpu.check_cycle = false;
  Log("Welcome to TaoHe Processor Core Verilating Model.");

#if CONFIG_WAVE_RECORD
  char wavefile_name[80];
  strcpy(wavefile_name, NPC_CHISEL);
  strcat(wavefile_name, "/out/waveform.vcd");
  Log("Wave Path: `%s`.", wavefile_name);

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
  case TCHE_INIT:
    reset();
    npc_state = TCHE_RUNNING;
    break;
  case TCHE_HALT:
    Log("Program already finished!");
    return;
  case TCHE_ABORT:
    Log("Program already aborted!");
    return;
  default:
    break;
  }

  npc_state = TCHE_RUNNING;

  switch (n) {
  case -1:
    while (npc_state == TCHE_RUNNING) {
      single_clock();
    }
    break;
  default:
    for (int i = 0; i < n; i++) {
      if (npc_state != TCHE_RUNNING) {
        break;
      }
      single_clock();
    }
    break;
  }
}

void cpu_sync() {
  memcpy(cpu.regs, cpu.top->TaoHe->registerFile->registers, sizeof(cpu.regs));
  cpu.inst = cpu.top->TaoHe->ifu->io_toIDU_bits_inst;
  cpu.check_cycle = cpu.top->TaoHe->ifu->iCount > cpu.iCount ||
                    npc_state == TCHE_HALT || npc_state == TCHE_ABORT;

  if (cpu.top->TaoHe->xbar->difftestSkip)
    difftest_skip_ref();

  if (cpu.check_cycle) {
    cpu.pc_prev = cpu.pc;
    cpu.pc = cpu.top->TaoHe->exu->io_toIFU_bits_nextPC;
    cpu.iCount = cpu.top->TaoHe->ifu->iCount;
  }
}

void cpu_check() {

#if CONFIG_DISASM
  disassembler();
#endif

  if (cpu.top->TaoHe->idu->decodeSupport == 0) {
    Log(ANSI_FMT("ERROR INST NOT SUPPORT: ", ANSI_FG_RED) ANSI_FG_BLUE
        "DECODE " ANSI_FMT("FAILED ", ANSI_FG_RED) ANSI_FG_BLUE
        "at pc = 0x%08X",
        cpu.pc_prev);
    npc_state = TCHE_ABORT;
  }

#if CONFIG_FTRACE
  ftrace_check();
#endif

#if CONFIG_DIFFTEST
  difftest_step(cpu.pc_prev, cpu.pc);
#endif

#if CONFIG_WATCHPOINT
  check_wp();
#endif

  finish();
}

void finish() {
  switch (npc_state) {
  case TCHE_ABORT:
    Log("TCHE: " ANSI_FMT("ABORT", ANSI_FG_RED) ANSI_FG_BLUE
        " at pc = 0x%08X " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED),
        cpu.pc);
    return;
  case TCHE_HALT:
    Log("TCHE: " ANSI_FMT("QUIT", ANSI_FG_GREEN) ANSI_FG_BLUE
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
