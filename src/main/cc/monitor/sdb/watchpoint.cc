/***************************************************************************************
 * Copyright (c) 2014-2022 Zihao Yu, Nanjing University
 *
 * NEMU is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan
 *PSL v2. You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
 *KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 *NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include <common.h>
#include <cpu/cpu.h>
#include <sdb.h>

#define NR_WP 32

static WP wp_pool[NR_WP] = {};
static WP *head = NULL, *free_ = NULL;

void init_wp_pool() {
  int i;
  for (i = 0; i < NR_WP; i++) {
    wp_pool[i].NO = i;
    wp_pool[i].next = (i == NR_WP - 1 ? NULL : &wp_pool[i + 1]);
    wp_pool[i].idle = true;
    wp_pool[i].expression[0] = '\0';
    wp_pool[i].result = 0;
  }

  head = NULL;
  free_ = wp_pool;

  Log("Watchpoint Pool initialized.");
}

WP *new_WP() {
  for (int i = 0; i < NR_WP; i++) {
    if (free_[i].idle) {
      WP *using_wp = head;
      if (using_wp == NULL) {
        using_wp = free_ + i;
        head = free_ + i;
        free_[i].idle = false;
        free_[i].next = NULL;
        return using_wp;
      } else {
        while (using_wp != NULL) {
          if (using_wp->next == NULL) {
            using_wp->next = &free_[i];
            free_[i].idle = false;
            break;
          } else {
            using_wp = using_wp->next;
          }
        }
      }
      free_[i].next = NULL;
      return using_wp->next;
    }
  }
  assert(0);
}

void free_wp(int wp_no) {
  Log("Enter free_wp func with int %i, head: %p", wp_no, head);
  WP *wp = head;
  if (head == NULL) {
    Log("Not fount: All watchpoints in idle.");
    return;
  } else if (wp->NO == wp_no) {
    head = wp->next;
  } else {
    while (wp != NULL && wp->NO != wp_no) {
      if (wp->next == NULL) {
        Log("Watchpoint not found, maybe in idle...");
        return;
      } else if (wp->next->NO == wp_no) {
        WP *temp = wp->next;
        wp->next = wp->next->next;
        wp = temp;
        break;
      }
      wp = wp->next;
    }
  }
  Log("Watchpoint found.");
  wp->idle = true;
  wp->expression[0] = '\0';
  wp->result = 0;
  wp->next = &free_[wp_no + 1];
  Log("Free watchpoint NO.%i", wp->NO);
}

void read_wp() {
  WP *wp = head;
  if (wp == NULL) {
    printf("No watchpoint set.\n");
  } else {
    while (wp != NULL) {
      printf("Watchpoint NO.%i: expression %s = value %u\n", wp->NO,
             wp->expression, wp->result);
      wp = wp->next;
    }
  }
}

void check_wp() {
  WP *wp = head;
  if (wp == NULL) {
    return;
  } else {
    bool pass = true;
    while (wp != NULL) {
      int result = expr(wp->expression, NULL);
      if (wp->result != result) {
        Log("Watchpoint NO.%i: expression %s value %u changed to %u", wp->NO,
            wp->expression, wp->result, result);
        wp->result = result;
        pass = false;
      }
      wp = wp->next;
    }

    if (!pass) {
      npc_state = SRIZ_PAUSE;
    }
  }
}
