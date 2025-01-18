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
#include <memory/paddr.h>
#include <sdb.h>

/* We use the POSIX regex functions to process regular expressions.
 * Type 'man regex' for more information about POSIX regex functions.
 */
#include <regex.h>

enum {
  TK_NOTYPE = 256,
  TK_EQ,
  TK_NEQ,
  TK_AND,
  TK_REG,
  TK_DEREF,
  TK_HEXNUM,
  TK_NUM,

  /* TODO: Add more token types */

};

static struct rule {
  const char *regex;
  int token_type;
} rules[] = {

    /* TODO: Add more rules.
     * Pay attention to the precedence level of different rules.
     */

    {"[[:space:]]+", TK_NOTYPE}, // space
    {"\\+", '+'},                // plus
    {"-", '-'},                  // sub
    {"\\*", '*'},                // times
    {"/", '/'},                  // div
    {"\\$[a-z|0-9]+", TK_REG},   // registers
    {"0x[0-9|A-F]+", TK_HEXNUM}, // hex-num
    {"[0-9]+", TK_NUM},          // num
    {"\\(", '('},                // left bracket
    {"\\)", ')'},                // right bracket
    {"==", TK_EQ},               // equal
    {"!=", TK_NEQ},              // not equal
    {"&&", TK_AND},              // and
};

#define NR_REGEX ARRLEN(rules)

static regex_t re[NR_REGEX] = {};

/* Rules are used for many times.
 * Therefore we compile them only once before any usage.
 */
void init_regex() {
  int i;
  char error_msg[128];
  int ret;

  for (i = 0; i < NR_REGEX; i++) {
    ret = regcomp(&re[i], rules[i].regex, REG_EXTENDED);
    if (ret != 0) {
      regerror(ret, &re[i], error_msg, 128);
      Log("regex compilation failed: %s\n%s", error_msg, rules[i].regex);
    }
  }

  Log("REGEX Compiling Success.");
}

typedef struct token {
  int type;
  char str[32];
} Token;

static Token tokens[128] __attribute__((used)) = {};
static int nr_token __attribute__((used)) = 0;

static bool make_token(char *e) {
  int position = 0;
  int i;
  regmatch_t pmatch;

  nr_token = 0;

  while (e[position] != '\0') {
    /* Try all rules one by one. */
    for (i = 0; i < NR_REGEX; i++) {
      if (regexec(&re[i], e + position, 1, &pmatch, 0) == 0 &&
          pmatch.rm_so == 0) {
        char *substr_start = e + position;
        int substr_len = pmatch.rm_eo;

        Token token = {rules[i].token_type};
        strncpy(token.str, substr_start, substr_len);
        token.str[substr_len] = '\0';

        // Log("match rules[%d] = \"%s\" at position %d with len %d: %.*s", i,
        //        rules[i].regex, position, substr_len, substr_len,
        //        substr_start);

        position += substr_len;

        /* TODO: Now a new token is recognized with rules[i]. Add codes
         * to record the token in the array `tokens'. For certain types
         * of tokens, some extra actions should be performed.
         */

        switch (rules[i].token_type) {
        case TK_NOTYPE:
          break;
        default:
          tokens[nr_token] = token;
          nr_token++;
        }

        break;
      }
    }

    if (i == NR_REGEX) {
      printf("no match at position %d\n%s\n%*.s^\n", position, e, position, "");
      return false;
    }
  }

  return true;
}

uint32_t eval(int p, int q);

word_t expr(char *e, bool *success) {
  if (!make_token(e)) {
    *success = false;
    return 0;
  }

  for (int i = 0; i < nr_token; i++) {
    if (tokens[i].type == '*' &&
        (i == 0 ||
         (tokens[i - 1].type != TK_NUM && tokens[i - 1].type != TK_HEXNUM &&
          tokens[i - 1].type != TK_REG && tokens[i - 1].type != ')'))) {
      tokens[i].type = TK_DEREF;
    }
  }

  word_t result = eval(0, nr_token - 1);

  return result;
}

bool check_parentheses(int p, int q) {
  int check_stack = 0;
  for (int i = p; i < q + 1; i++) {
    if (tokens[i].type == '(') {
      check_stack++;
    } else if (tokens[i].type == ')') {
      check_stack--;
    }

    if (check_stack == 0 && i != q) {
      return false;
    }
  }
  if (check_stack != 0) {
    Log("Parentheses error.");
  }
  return true;
}

uint32_t eval(int p, int q) {
  if (p > q) {
    /* Bad expression */
  } else if (p == q) {
    /* Single token.
     * For now this token should be a number.
     * Return the value of the number.
     */
    bool success = false;
    switch (tokens[p].type) {
    case TK_REG:
      return isa_reg_str2val(tokens[p].str, &success);
    case TK_HEXNUM:
      return strtoul(tokens[p].str, NULL, 16);
    case TK_NUM:
      return strtoul(tokens[p].str, NULL, 10);
    }
  } else if (check_parentheses(p, q) == true) {
    /* The expression is surrounded by a matched pair of parentheses.
     * If that is the case, just throw away the parentheses.
     */
    return eval(p + 1, q - 1);
  } else if (tokens[p].type == TK_DEREF && q == p + 1) {
    vaddr_t upos = strtol(tokens[q].str, NULL, 16);
    return paddr_read(upos, 4);
  } else {
    int op = -1;
    bool imux = false;
    int bmux = 0;
    bool lmux = false;
    for (int i = p; i < q + 1; i++) {
      if ((tokens[i].type == TK_EQ || tokens[i].type == TK_NEQ ||
           tokens[i].type == TK_AND) &&
          bmux == 0) {
        op = i;
        lmux = true;
      } else if ((tokens[i].type == '+' || tokens[i].type == '-') &&
                 bmux == 0 && !lmux) {
        op = i;
        imux = true;
      } else if ((tokens[i].type == '*' || tokens[i].type == '/') && !imux &&
                 !lmux && bmux == 0) {
        op = i;
      } else if (tokens[i].type == '(') {
        bmux += 1;
      } else if (tokens[i].type == ')') {
        bmux -= 1;
      }
    }
    if (op == -1) {
      return 77777; // Magic number 77777 for debug.
    }
    uint32_t val1 = eval(p, op - 1);
    uint32_t val2 = eval(op + 1, q);

    switch (tokens[op].type) {
    case '+':
      return val1 + val2;
    case '-':
      return val1 - val2;
    case '*':
      return val1 * val2;
    case '/':
      if (val2 == 0) {
        return 77777; // Magic number 77777 for debug.
      } else {
        return val1 / val2;
      }
    case TK_EQ:
      return val1 == val2;
    case TK_NEQ:
      return val1 != val2;
    case TK_AND:
      return val1 && val2;
    default:
      assert(0);
    }
  }

  return 1;
}
