#include "common.h"
#include <elf.h>
#ifndef __CPU_FTRACE_H__

// Public

void ftrace_init(char *path);
void ftrace_check();

// Internal
bool get_ftrace_enable();

void elf_parse(char *path);
Elf32_Sym *get_symtab();
int get_symnum();
char *get_strtab();

void ftrace_link_table_build();

#endif