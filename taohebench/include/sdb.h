#ifndef __SDB_H__
#define __SDB_H__

#include "common.h"

void sdb_mainloop();
void init_wp_pool();
void init_regex();
word_t expr(char *e, bool *success);

typedef struct watchpoint {
  int NO;
  struct watchpoint *next;
  bool idle;
  char expression[32];
  word_t result;
} WP;

void batch_mode_enable();

WP *new_WP();
void free_wp(int wp_no);
void read_wp();
void check_wp();
void expr_check();

#endif