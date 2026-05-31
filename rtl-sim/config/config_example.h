// This file should rename to config.h to pass the compilation & configurate the
// NPC.

// run in batch mode
#define CONFIG_BATCH_MODE 0

#define CONFIG_TRACERS 0

// light-weight tracer
#define CONFIG_DISASM 1 && CONFIG_TRACERS
#define CONFIG_FTRACE 0 && CONFIG_TRACERS
#define CONFIG_MTRACE 1 && CONFIG_TRACERS
#define CONFIG_WATCHPOINT 1 && CONFIG_TRACERS

// more heavy tools
#define CONFIG_WAVE_RECORD 0
#define CONFIG_DIFFTEST 0

// enable device
#define CONFIG_DEVICE 0

// define the address of the memory-mapped I/O devices
#define CONFIG_SERIAL_MMIO 0xa00003f8
#define CONFIG_RTC_MMIO 0xa0000048
