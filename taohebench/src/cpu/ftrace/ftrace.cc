#include <common.h>
#include <cpu/cpu.h>
#include <cpu/ftrace.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct FunctionNode {
  char *name;
  vaddr_t pos;
  struct FunctionNode *next;
} FunctionNode;

typedef struct FunctionCallNode {
  word_t ra;
  char *funcname;

  struct FunctionCallNode *next;
} FunctionCallNode;

static FunctionNode *function_head = NULL;
static FunctionCallNode *function_call_head = NULL;

void function_link_table_insert(FunctionNode *node);

void ftrace_new_call(vaddr_t dnpc);
void ftrace_return(vaddr_t dnpc);

void ftrace_link_table_build() {
  Elf32_Sym *symtab = get_symtab();
  int symnum = get_symnum();
  char *strtab = get_strtab();

  for (int i = 0; i < symnum; i++) {
    if (ELF32_ST_TYPE(symtab[i].st_info) != STT_FUNC) {
      continue;
    }

    FunctionNode *node = (FunctionNode *)malloc(sizeof(FunctionNode));
    node->name = strtab + symtab[i].st_name;
    node->pos = symtab[i].st_value;
    node->next = NULL;

    Log("Find function %s@0x%X", node->name, node->pos);
    function_link_table_insert(node);
  }

  return;
}

void ftrace_check() {
  if (get_ftrace_enable() != true) {
    return;
  }

  word_t inst = cpu.inst;

  if (inst == 0x00008067) { // ret: 0x00008067 jalr zero, (0)ra
    ftrace_return(cpu.pc);
  } else if ((inst & 0x00000fff) == 0x000000ef) { // jal ra, imm
    ftrace_new_call(cpu.pc);
  } else if ((inst & 0x00000fff) == 0x000000e7) { // jalr ra, imm(..)
    ftrace_new_call(cpu.pc);
  }
}

void function_link_table_insert(FunctionNode *node) {

  if (function_head == NULL) {
    function_head = node;
    return;
  }

  FunctionNode *current = function_head;
  while (current->next != NULL) {
    current = current->next;
  }

  current->next = node;

  return;
}

void ftrace_new_call(vaddr_t dnpc) {

  FunctionNode *current = function_head;
  FunctionCallNode *node = (FunctionCallNode *)malloc(sizeof(FunctionCallNode));
  node->funcname = (char *)"???";

  while (current != NULL) {
    if (current->pos == dnpc) {
      node->funcname = current->name;
      node->ra = cpu.pc_prev + 4;
      node->next = NULL;

      break;
    }
    current = current->next;
  }
  Log("0x%X: Call function %s@0x%X, return in 0x%X.", cpu.pc_prev,
      node->funcname, dnpc, cpu.pc_prev + 4);

  if (function_call_head == NULL) {
    function_call_head = node;
    return;
  }

  node->next = function_call_head;
  function_call_head = node;

  return;
}

void ftrace_return(vaddr_t dnpc) {
  FunctionCallNode *current = function_call_head;

  if (current == NULL) {
    return;
  }

  if (current->ra == dnpc) {
    Log("0x%X: Return from function %s.", cpu.pc, current->funcname);
    function_call_head = current->next;
    free(current);
    return;
  }

  while (current->next != NULL) {
    if (current->next->ra == dnpc) {
      Log("0x%X: Return from function %s.\n", cpu.pc, current->next->funcname);
      FunctionCallNode *tmp = current->next;
      current->next = tmp->next;
      tmp->next = NULL;
      free(tmp);
      return;
    }

    current = current->next;
  }

  Log("0x%X: Return from function %s.", cpu.pc, "???");
  return;
}