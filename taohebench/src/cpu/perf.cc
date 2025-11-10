#include <common.h>
#include <cpu/cpu.h>
#include <cpu/perf.h>
#include <string>
#include <time.h>
#include <toml++/toml.h>

toml::table perf_record;

double freq;
double area;

struct {
  uint32_t sec;
  uint32_t usec;
} running_time;

struct timespec start_time, end_time;

void perf_init() {
  char *record_path = strcat(getenv("NPC_CHISEL"), "/out/perf.toml");
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

void perf_stat() {

  double cpi = (double)cpu.total_cycle / (double)cpu.iCount;
  double ipc = (double)cpu.iCount / (double)cpu.total_cycle;

  // In milliseconds
  double exec_time = (double)cpu.total_cycle / (freq * 1e6) * 1e3;
  double sim_time = (double)(running_time.sec * 1e6 + running_time.usec) / 1e3;

  double exec_mips = (double)cpu.iCount / exec_time / 1e3;
  double sim_mips = (double)cpu.iCount / (sim_time * 1e-3) / 1e3;

  double sim_freq = (double)cpu.total_cycle / (sim_time * 1e-3) / 1e3;

  Log("Performance Statistics:");
  Log("Total Instructions: %d for %d Cycles", cpu.iCount, cpu.total_cycle);
  Log("CPI: %.4f, IPC: %.4f", cpi, ipc);
  Log("Expected Execution Time: %.4f ms in %.2f MHz Frequency for %.2f MIPS",
      exec_time, freq, exec_mips);
  Log("Real Simulation Time: %.4f ms in %.2f KHz Frequency for %.2f KIPS",
      sim_time, sim_freq, sim_mips);
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
