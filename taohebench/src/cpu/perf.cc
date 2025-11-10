#include <common.h>
#include <cpu/cpu.h>
#include <cpu/perf.h>
#include <string>
#include <toml++/toml.h>

toml::table perf_record;

double freq;
double area;

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

  Log("CPU Frequency: %.2f MHz", freq);
  Log("CPU Area: %.2f um^2", area);
}

void perf_stat() {
  double cpi = (double)cpu.total_cycle / (double)cpu.iCount;
  double ipc = (double)cpu.iCount / (double)cpu.total_cycle;

  Log("Performance Statistics:");
  Log("Total Instructions: %d", cpu.iCount);
  Log("Total Cycles: %d", cpu.total_cycle);
  Log("CPI: %.4f, iPC: %.4f", cpi, ipc);
  Log("Expected Execution Time: %.4f ms",
      (double)cpu.total_cycle / (freq * 1e6) * 1e3);
}
