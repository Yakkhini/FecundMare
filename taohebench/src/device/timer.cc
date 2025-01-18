#include <common.h>
#include <ctime>
#include <device/device.h>
#include <time.h>

NPCTime npc_time;
struct timespec *ts;

void timer_update() {
  clock_gettime(CLOCK_REALTIME, ts);
  npc_time.sec = ts->tv_sec;
  npc_time.usec = ts->tv_nsec / 1000;
}

void timer_init() {
  ts = (struct timespec *)malloc(sizeof(struct timespec));
  timer_update();
}