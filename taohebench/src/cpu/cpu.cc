#include <common.h>
#include <cpu/cpu.h>
#include <cpu/difftest.h>
#include <cpu/disasm.h>
#include <cpu/ftrace.h>
#include <cpu/mtrace.h>
#include <cpu/perf.h>
#include <memory/vaddr.h>
#include <nvboard.h>
#include <sdb.h>
#include <signal.h>

#ifdef CONFIG_TARGET_ysyxSoCFull
#include <VysyxSoCFull.h>
#include <VysyxSoCFull__Dpi.h>
#include <VysyxSoCFull__Syms.h>
#endif

#ifdef CONFIG_TARGET_TaoHe
#include <VTaoHe.h>
#include <VTaoHe__Dpi.h>
#include <VTaoHe__Syms.h>
#endif

CPU cpu;
core_symbol_t *cpu_symbol;
NPCState npc_state = TCHE_INIT;

static char *NPC_CHISEL = getenv("NPC_CHISEL");
static VerilatedContext *contextp;

#if CONFIG_WAVE_RECORD
static VerilatedFstC *tfp;
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

#ifdef CONFIG_TARGET_ysyxSoCFull
  nvboard_update();
#endif

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

  cpu.total_cycle++;

  cpu_sync();
  cpu_check();
}

void reset() {

#if CONFIG_WAVE_RECORD
  contextp->timeInc(1);
#endif

  cpu.top->reset = 1;

  for (int i = 0; i < 15; i++) {

#ifdef CONFIG_TARGET_ysyxSoCFull
    nvboard_update();
#endif

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

    cpu.top->clock = 0;
    cpu.top->eval();
  }

#if CONFIG_WAVE_RECORD
  tfp->dump(contextp->time());
  contextp->timeInc(1);
#endif

  cpu.top->reset = 0;
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

  Log("TCHE reset done.");
}

void cpu_init(int argc, char **argv) {
#if CONFIG_DISASM
  disasm_init();
#endif

  contextp = new VerilatedContext;
  contextp->commandArgs(argc, argv);

#ifdef CONFIG_TARGET_ysyxSoCFull
  cpu.top = new VysyxSoCFull(contextp);
  cpu_symbol = cpu.top->ysyxSoCFull->asic->cpu->cpu;
#endif

#ifdef CONFIG_TARGET_TaoHe
  cpu.top = new VTaoHe(contextp);
  cpu_symbol = cpu.top->TaoHe;
#endif
  cpu.iCount = 0;
  cpu.total_cycle = 0;
  cpu.check_cycle = false;
  perf_init();
  mtrace_init();
  Log("Welcome to TaoHe Processor Core Verilating Model.");

#if CONFIG_WAVE_RECORD
  char wavefile_name[80];
  strcpy(wavefile_name, NPC_CHISEL);
  strcat(wavefile_name, "/out/waveform.fst");
  Log("Wave Path: `%s`.", wavefile_name);

  Verilated::traceEverOn(true);
  tfp = new VerilatedFstC;
  cpu.top->trace(tfp, 5);

  tfp->open(wavefile_name);
#else
  Log("Waveform Recording: " ANSI_FMT("DISABLE", ANSI_FG_GREEN) ANSI_FG_BLUE);
#endif
}

void handle_sigint(int sig) {
  if (npc_state == TCHE_RUNNING && sig == SIGINT) {
    printf("\n"); // Print a newline for avoiding `^C` symbol.
    Log("TCHE " ANSI_FMT("USER INTERRUPT", ANSI_FG_YELLOW) ANSI_FG_BLUE
        " at pc = 0x%08X ",
        cpu.pc);
    npc_state = TCHE_PAUSE;
  }
}

void cpu_exec(int n) {
  signal(SIGINT, handle_sigint);
  switch (npc_state) {
  case TCHE_INIT:
    perf_start_timer();
    reset();
    perf_end_timer();
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
    perf_start_timer();
    while (npc_state == TCHE_RUNNING) {
      single_clock();
    }
    perf_end_timer();
    break;
  default:
    perf_start_timer();
    for (int i = 0; i < n; i++) {
      if (npc_state != TCHE_RUNNING) {
        break;
      }
      single_clock();
    }
    perf_end_timer();
    break;
  }

  if (npc_state == TCHE_HALT || npc_state == TCHE_ABORT) {
    finish();
  }
}

void cpu_sync() {
  memcpy(cpu.regs, cpu_symbol->registerFile->registers, sizeof(cpu.regs));
  cpu.inst = cpu_symbol->ifu->io_toIDU_bits_inst;
  cpu.check_cycle = cpu_symbol->ifu->iCount > cpu.iCount ||
                    npc_state == TCHE_HALT || npc_state == TCHE_ABORT;

  if (cpu.check_cycle) {
    cpu.pc_prev = cpu.pc;
    cpu.pc = cpu_symbol->ifu->pc;
    cpu.iCount = cpu_symbol->ifu->iCount;
  }

  if (npc_state == TCHE_HALT) {
    // Increase PC value since simulation will halt in this cycle immediately,
    // without WB or Next IF stage.
    cpu.pc_prev = cpu.pc;
    cpu.pc += 0x4;
  }

  axi4_interface_sync(cpu_symbol);
}

void cpu_check() {

  if (cpu_symbol->idu->decodeSupport == 0) {
    Log(ANSI_FMT("ERROR INST NOT SUPPORT: ", ANSI_FG_RED) ANSI_FG_BLUE
        "DECODE " ANSI_FMT("FAILED ", ANSI_FG_RED) ANSI_FG_BLUE
        "at pc = 0x%08X",
        cpu.pc_prev);
    npc_state = TCHE_ABORT;
  }

  if (cpu_symbol->exu->haltUnit->halt) {
    halt(cpu_symbol->exu->haltUnit->code);
  }

#if CONFIG_MTRACE || CONFIG_DIFFTEST || CONFIG_TARGET_TaoHe
  mtrace();
#endif

  if (cpu.check_cycle == false) {
    return;
  }

#if CONFIG_DISASM
  disassembler();
#endif

#if CONFIG_FTRACE
  ftrace_check();
#endif

#if CONFIG_DIFFTEST
  if (cpu_symbol->exu->difftestSkip) {
    difftest_skip_ref();
  } else {
    difftest_step(cpu.pc_prev, cpu.pc);
  }
#endif

#if CONFIG_WATCHPOINT
  check_wp();
#endif

  cpu.check_cycle = false;

  return;
}

void finish() {
  switch (npc_state) {
  case TCHE_ABORT:
    Log("TCHE: " ANSI_FMT("ABORT", ANSI_FG_RED) ANSI_FG_BLUE
        " at pc = 0x%08X " ANSI_FMT("HIT BAD TRAP", ANSI_FG_RED),
        cpu.pc);
    break;
  case TCHE_HALT:
    Log("TCHE: " ANSI_FMT("QUIT", ANSI_FG_GREEN) ANSI_FG_BLUE
        " at pc = 0x%08X " ANSI_FMT("HIT GOOD TRAP", ANSI_FG_GREEN),
        cpu.pc);
    break;
  default:
    return;
  }

  mtrace_finish();
  perf_stat(cpu_symbol);
}

void cpu_exit() {
#if CONFIG_WAVE_RECORD
  tfp->close();
#endif

  cpu.top->final();
  delete cpu.top;

  disasm_exit();
}
