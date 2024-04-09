#ifndef __MEMORY_VADDR_H__
#define __MEMORY_VADDR_H__

#include <common.h>

word_t vaddr_ifetch(vaddr_t addr);
void mtrace();

#endif