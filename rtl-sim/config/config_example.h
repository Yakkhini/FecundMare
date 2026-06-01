// This file should rename to config.h to pass the compilation & configurate the
// NPC.

// run in batch mode
#define CONFIG_BATCH_MODE 1

#define CONFIG_TRACERS 0

// light-weight tracer
#define CONFIG_DISASM 0 && CONFIG_TRACERS
#define CONFIG_FTRACE 0 && CONFIG_TRACERS
#define CONFIG_WATCHPOINT 0 && CONFIG_TRACERS

// Memory trace
#define CONFIG_MTRACE 0 && CONFIG_TRACERS
#define CONFIG_MTRACE_FLASH (0 || CONFIG_MTRACE) && CONFIG_TRACERS
#define CONFIG_MTRACE_DB 0

// more heavy tools
#define CONFIG_WAVE_RECORD 0
#define CONFIG_DIFFTEST 0

// enable device
#define CONFIG_DEVICE 1
#define CONFIG_NVBOARD 0 && CONFIG_TARGET_ysyxSoCFull

// define the address of the memory-mapped I/O devices
#define CONFIG_SERIAL_MMIO 0x10000000
#define CONFIG_RTC_MMIO 0xa0000048

// define the address of the virtual SIMD device
// Range: [0xa2000000, 0xa2ffffff]
#define CONFIG_VSIMD_MMIO 0xa2000000

#define CONFIG_SILENT 1
