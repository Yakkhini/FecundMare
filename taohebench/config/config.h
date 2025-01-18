// run in batch mode
#define CONFIG_BATCH_MODE 0

#define CONFIG_TRACERS 1

// light-weight tracer
#define CONFIG_DISASM 0 && CONFIG_TRACERS
#define CONFIG_FTRACE 0 && CONFIG_TRACERS
#define CONFIG_MTRACE 0 && CONFIG_TRACERS
#define CONFIG_WATCHPOINT 0 && CONFIG_TRACERS

// more heavy tools
#define CONFIG_WAVE_RECORD 0
#define CONFIG_DIFFTEST 1

// enable device
#define CONFIG_DEVICE 0

// define the address of the memory-mapped I/O devices
#define CONFIG_SERIAL_MMIO 0xa00003f8
#define CONFIG_RTC_MMIO 0xa0000048
