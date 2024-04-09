#include <common.h>
#include <cpu/cpu.h>
#include <memory/paddr.h>
#include <readline/history.h>
#include <readline/readline.h>
#include <sdb.h>

static bool batch_mode = false;

void batch_mode_enable() { batch_mode = true; }

static char *rl_gets() {
  static char *line_read = NULL;

  if (line_read) {
    free(line_read);
    line_read = NULL;
  }

  line_read = readline("\%sriz-npc > ");

  if (line_read && *line_read) {
    add_history(line_read);
  }

  return line_read;
}

static int cmd_c(char *args) {
  cpu_exec(-1);
  return 0;
}

static int cmd_d(char *args) {
  char *arg = strtok(NULL, " ");
  int wp_no = atoi(arg);
  free_wp(wp_no);
  return 0;
}

static int cmd_si(char *args) {
  char *arg = strtok(NULL, " ");
  if (arg == NULL) {
    cpu_exec(1);
    return 0;
  }
  int istep = atoi(arg);
  printf("Excute program by %i step\n", istep);
  cpu_exec(istep);
  return 0;
}

static int cmd_info(char *args) {
  char *arg = strtok(NULL, " ");
  if (arg == NULL) {
    printf("Please give info subcommand.\n");
  } else if (strcmp(arg, "r") == 0) {
    isa_reg_display();
  } else if (strcmp(arg, "w") == 0) {
    read_wp();
  } else {
    printf("Incorrect info subcommand.\n");
  }

  return 0;
}

static int cmd_p(char *args) {
  bool success = true;
  word_t result = expr(args, &success);
  if (success) {
    printf("Result is: %u.\n", result);
  } else {
    printf("Parse expression failed.");
  }
  return 0;
}

static int cmd_q(char *args) { return -1; }

static int cmd_w(char *args) {
  WP *wp = new_WP();
  bool success = true;
  strcpy(wp->expression, args);
  wp->result = expr(wp->expression, &success);
  if (success) {
    Log("New watchpoint in NO.%i with expression %s equal to value %u", wp->NO,
        wp->expression, wp->result);
  } else {
    Log("Expression error, delete watchpoint.");
    free_wp(wp->NO);
  }
  return 0;
}

static int cmd_x(char *args) {
  char *len = strtok(NULL, " ");
  char *pos = strtok(NULL, " ");
  int ilen = strtol(len, NULL, 10);
  vaddr_t upos = strtol(pos, NULL, 16);
  printf("Reading %i data begin at 0x%X...\n", ilen, upos);
  for (int i = 0; i < ilen; i++) {
    printf("0x%08X: 0x%08X\n", upos + i, paddr_read(upos + i, 4));
  }
  return 0;
}

static int cmd_help(char *args);

#define NR_CMD ARRLEN(cmd_table)
static struct {
  const char *name;
  const char *description;
  int (*handler)(char *);
} cmd_table[] = {
    {"help", "Display information about all supported commands", cmd_help},
    {"c", "Continue the execution of the program", cmd_c},
    {"d", "Free watchpoint", cmd_d},
    {"q", "Exit SRIZ Simulator", cmd_q},
    {"p", "Caculate expression and output value.", cmd_p},
    {"si", "Execute program by step", cmd_si},
    {"info", "Print register info", cmd_info},
    {"x", "Read memory.", cmd_x},
    {"w", "Set new watchpoint.", cmd_w},

};

static int cmd_help(char *args) {
  /* extract the first argument */
  char *arg = strtok(NULL, " ");
  int i;

  if (arg == NULL) {
    /* no argument given */
    for (i = 0; i < NR_CMD; i++) {
      printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
    }
  } else {
    for (i = 0; i < NR_CMD; i++) {
      if (strcmp(arg, cmd_table[i].name) == 0) {
        printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
        return 0;
      }
    }
    printf("Unknown command '%s'\n", arg);
  }
  return 0;
}

void sdb_mainloop() {
  if (batch_mode) {
    cpu_exec(-1);
    return;
  }

  for (char *str; (str = rl_gets()) != NULL;) {
    char *str_end = str + strlen(str);

    /* extract the first token as the command */
    char *cmd = strtok(str, " ");
    if (cmd == NULL) {
      continue;
    }

    /* treat the remaining string as the arguments,
     * which may need further parsing
     */
    char *args = cmd + strlen(cmd) + 1;
    if (args >= str_end) {
      args = NULL;
    }

    int i;
    for (i = 0; i < NR_CMD; i++) {
      if (strcmp(cmd, cmd_table[i].name) == 0) {
        if (cmd_table[i].handler(args) < 0) {
          return;
        }
        break;
      }
    }

    if (i == NR_CMD) {
      printf("Unknown command '%s'\n", cmd);
    }
  }
}
