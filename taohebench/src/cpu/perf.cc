#include <common.h>
#include <cpu/cpu.h>
#include <cpu/perf.h>
#include <cstdint>
#include <string>
#include <time.h>
#include <toml++/toml.h>

char record_path[80];
toml::table perf_record;

double freq;
double area;

struct {
  uint32_t sec;
  uint32_t usec;
} running_time;

struct timespec start_time, end_time;
void perf_counter_stat(core_symbol_t *cpu_symbol);

void perf_init() {
  strcpy(record_path, getenv("NPC_CHISEL"));
  strcat(record_path, "/out/perf.toml");
  try {
    perf_record = toml::parse_file(record_path);
  } catch (const toml::parse_error &err) {
    printf("Error parsing TOML file: %s\n",
           std::string(err.description()).c_str());
  }

  freq = perf_record["TaoHe"]["freq(MHz)"].value<double>().value();
  area = perf_record["TaoHe"]["area(um^2)"].value<double>().value();

  running_time.sec = 0;
  running_time.usec = 0;

  Log("CPU Frequency: %.2f MHz", freq);
  Log("CPU Area: %.2f um^2", area);
}

void perf_stat(core_symbol_t *cpu_symbol) {

  double cpi = (double)cpu.total_cycle / (double)cpu.iCount;
  double ipc = (double)cpu.iCount / (double)cpu.total_cycle;

  // In milliseconds
  double exec_time = (double)cpu.total_cycle / (freq * 1e6) * 1e3;
  double sim_time = (double)(running_time.sec * 1e6 + running_time.usec) / 1e3;

  double exec_mips = (double)cpu.iCount / exec_time / 1e3;
  double sim_mips = (double)cpu.iCount / (sim_time * 1e-3) / 1e3;

  double sim_freq = (double)cpu.total_cycle / (sim_time * 1e-3) / 1e3;

  Log("Performance Statistics:");
  Log("Total Instructions: %u for %u Cycles", cpu.iCount, cpu.total_cycle);
  Log("CPI: %.4f, IPC: %.4f", cpi, ipc);
  Log("Expected Execution Time: %.4f ms in %.2f MHz Frequency for %.2f MIPS",
      exec_time, freq, exec_mips);
  Log("Real Simulation Time: %.4f ms in %.2f KHz Frequency for %.2f KIPS",
      sim_time, sim_freq, sim_mips);
  perf_counter_stat(cpu_symbol);
}

void perf_start_timer() { clock_gettime(CLOCK_REALTIME, &start_time); }

void perf_end_timer() {
  clock_gettime(CLOCK_REALTIME, &end_time);
  int32_t sec_diff = end_time.tv_sec - start_time.tv_sec;
  int32_t usec_diff = (end_time.tv_nsec - start_time.tv_nsec) / 1000;
  if (usec_diff < 0) {
    sec_diff -= 1;
    usec_diff += 1000000;
  } else if (usec_diff >= 1000000) {
    sec_diff += usec_diff / 1000000;
    usec_diff = usec_diff % 1000000;
  }
  running_time.sec += sec_diff;
  running_time.usec += usec_diff;
}

toml::table perf_branch_table(uint32_t inst_count, uint32_t inst_cycle_count) {
  toml::table table;

  double average_cycle = (double)inst_cycle_count / (double)inst_count;
  double inst_persentage = (double)inst_count / (double)cpu.iCount * 100.0;
  double cycle_persentage =
      (double)inst_cycle_count / (double)cpu.total_cycle * 100.0;

  table.insert_or_assign("Average Cycle per Inst", average_cycle);
  table.insert_or_assign("Inst Count", inst_count);
  table.insert_or_assign("Inst Percentage", inst_persentage);
  table.insert_or_assign("Cycle Count", inst_cycle_count);
  table.insert_or_assign("Cycle Percentage", cycle_persentage);

  return table;
}

void perf_counter_stat(core_symbol_t *cpu_symbol) {
  uint32_t ifu_fetch_inst_count = cpu_symbol->ifu->fetchInstNumCounter;
  uint32_t ifu_fetch_waiting_cycle = cpu_symbol->ifu->fetchWaitingCycleCounter;
  uint32_t ifu_icache_hit_counter = cpu_symbol->iCache->iCacheHitCounter;
  uint32_t ifu_icache_miss_counter = cpu_symbol->iCache->iCacheMissCounter;
  uint32_t ifu_icache_tmt_counter = cpu_symbol->iCache->iCacheTMTCounter;
  double ifu_icache_amat =
      (double)ifu_fetch_waiting_cycle / (double)ifu_fetch_inst_count;
  double ifu_icache_amp =
      (double)ifu_icache_tmt_counter / (double)ifu_icache_miss_counter;

  uint32_t idu_branch_inst_count = cpu_symbol->idu->branchInstCounter;
  uint32_t idu_jump_inst_count = cpu_symbol->idu->jumpInstCounter;
  uint32_t idu_load_inst_count = cpu_symbol->idu->loadInstCounter;
  uint32_t idu_store_inst_count = cpu_symbol->idu->storeInstCounter;
  uint32_t idu_arith_inst_count = cpu_symbol->idu->arithInstCounter;

  uint32_t exu_arith_done_count = cpu_symbol->exu->arithmeticDoneCounter;

  uint32_t lsu_load_valid_count = cpu_symbol->lsu->loadDataValidCounter;
  uint32_t lsu_load_waiting_cycle = cpu_symbol->lsu->loadWaitingCycleCounter;
  uint32_t lsu_store_waiting_cycle = cpu_symbol->lsu->storeWaitingCycleCounter;

  uint32_t arbiter_ifu_axi_waiting_cycle =
      cpu_symbol->axiArbiter->ifuAXIWaitingCycleCounter;
  uint32_t arbiter_ifu_arbiter_waiting_cycle =
      cpu_symbol->axiArbiter->ifuArbiterWaitingCycleCounter;
  uint32_t arbiter_lsu_axi_load_waiting_cycle =
      cpu_symbol->axiArbiter->lsuAXILoadWaitingCycleCounter;
  uint32_t arbiter_lsu_axi_store_waiting_cycle =
      cpu_symbol->axiArbiter->lsuAXIStoreWaitingCycleCounter;
  uint32_t arbiter_lsu_arbiter_load_waiting_cycle =
      cpu_symbol->axiArbiter->lsuArbiterLoadWaitingCycleCounter;
  uint32_t arbiter_lsu_arbiter_store_waiting_cycle =
      cpu_symbol->axiArbiter->lsuArbiterStoreWaitingCycleCounter;

  auto ifu_table =
      toml::table{{"fetchInstNumCounter", ifu_fetch_inst_count},
                  {"fetchWaitingCycleCounter", ifu_fetch_waiting_cycle},
                  {"Cache Hit", ifu_icache_hit_counter},
                  {"Cache Miss", ifu_icache_miss_counter},
                  {"Cache TMT", ifu_icache_tmt_counter},
                  {"Cache AMAT", ifu_icache_amat},
                  {"Cache AMP", ifu_icache_amp}};

  auto idu_table =
      toml::table{{"Jump Inst Count", idu_jump_inst_count},
                  {"Jump Inst Percentage",
                   (double)idu_jump_inst_count / (double)cpu.iCount * 100.0},
                  {"Branch Inst Count", idu_branch_inst_count},
                  {"Branch Inst Percentage",
                   (double)idu_branch_inst_count / (double)cpu.iCount * 100.0},
                  {"Load Inst Count", idu_load_inst_count},
                  {"Load Inst Percentage",
                   (double)idu_load_inst_count / (double)cpu.iCount * 100.0},
                  {"Store Inst Count", idu_store_inst_count},
                  {"Store Inst Percentage",
                   (double)idu_store_inst_count / (double)cpu.iCount * 100.0},
                  {"Arithmetic Inst Count", idu_arith_inst_count},
                  {"Arithmetic Inst Percentage",
                   (double)idu_arith_inst_count / (double)cpu.iCount * 100.0}};

  auto exu_table = toml::table{
      {"arithmeticDoneCounter", exu_arith_done_count},
      {"Memory Stall",
       perf_stall_table(exu_memory_done_count, exu_memory_stall_cycle_count)}};

  auto lsu_table =
      toml::table{{"loadDataValidCounter", lsu_load_valid_count},
                  {"loadWaitingCycleCounter", lsu_load_waiting_cycle},
                  {"storeWaitingCycleCounter", lsu_store_waiting_cycle}};

  auto axi_arbiter_table = toml::table{
      {"ifuAXIWaitingCycleCounter", arbiter_ifu_axi_waiting_cycle},
      {"ifuArbiterWaitingCycleCounter", arbiter_ifu_arbiter_waiting_cycle},
      {"lsuAXILoadWaitingCycleCounter", arbiter_lsu_axi_load_waiting_cycle},
      {"lsuAXIStoreWaitingCycleCounter", arbiter_lsu_axi_store_waiting_cycle},
      {"lsuArbiterLoadWaitingCycleCounter",
       arbiter_lsu_arbiter_load_waiting_cycle},
      {"lsuArbiterStoreWaitingCycleCounter",
       arbiter_lsu_arbiter_store_waiting_cycle}};

  auto taohe_perf_record = perf_record.get("TaoHe")->as_table();

  taohe_perf_record->insert_or_assign("Total Cycle", cpu.total_cycle);
  taohe_perf_record->insert_or_assign("Total Instructions", cpu.iCount);
  taohe_perf_record->insert_or_assign("CPI", (double)cpu.total_cycle /
                                                 (double)cpu.iCount);
  taohe_perf_record->insert_or_assign("IPC", (double)cpu.iCount /
                                                 (double)cpu.total_cycle);
  taohe_perf_record->insert_or_assign("IFU", ifu_table);
  taohe_perf_record->insert_or_assign("IDU", idu_table);
  taohe_perf_record->insert_or_assign("EXU", exu_table);
  taohe_perf_record->insert_or_assign("LSU", lsu_table);
  taohe_perf_record->insert_or_assign("AXI Arbiter", axi_arbiter_table);

  std::ofstream perf_file;
  perf_file.open(record_path, std::ios::out);
  perf_file << toml::toml_formatter(perf_record) << std::endl;

  perf_file.close();
}
