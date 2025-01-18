#include <assert.h>
#include <common.h>
#include <cpu/ftrace.h>
#include <elf.h>
#include <stdio.h>
#include <stdlib.h>

static char *elf_file;

static int check;

static Elf32_Ehdr *elf_hdr;
static Elf32_Shdr *section_hdrs;
static Elf32_Shdr shstrtab_shdr;
static char *shstrtab;

static Elf32_Shdr symtab_shdr;
static Elf32_Shdr strtab_shdr;

static Elf32_Sym *symtab;
static int symnum;
static char *strtab;

void parse_elf_header();
void parse_shstrtab_shdr();
void parse_shstrtab();
void parse_symtab_strtab_hdr();
void parse_symtab();
void parse_strtab();

void elf_parse(char *path) {
  Log("Parsing ELF file...");

  FILE *file = fopen(path, "rb");
  fseek(file, 0, SEEK_END);
  int fsize = ftell(file);

  elf_file = (char *)malloc(fsize);
  fseek(file, 0, SEEK_SET);
  check = fread(elf_file, fsize, 1, file);
  assert(check == 1);
  fclose(file);

  parse_elf_header();
  parse_shstrtab_shdr();
  parse_shstrtab();
  parse_symtab_strtab_hdr();

  parse_symtab();
  parse_strtab();

  Log("Parsing ELF done.");
  return;
}

void parse_elf_header() {

  elf_hdr = (Elf32_Ehdr *)elf_file;

  return;
}

void parse_shstrtab_shdr() {
  section_hdrs = (Elf32_Shdr *)(elf_file + elf_hdr->e_shoff);
  return;
}

void parse_shstrtab() {
  shstrtab_shdr = section_hdrs[elf_hdr->e_shstrndx];
  shstrtab = elf_file + shstrtab_shdr.sh_offset;

  return;
}

void parse_symtab_strtab_hdr() {

  for (int i = 0; i < elf_hdr->e_shnum; i++) {
    if (section_hdrs[i].sh_type == SHT_SYMTAB) {
      symtab_shdr = section_hdrs[i];
    }

    if (strcmp(shstrtab + section_hdrs[i].sh_name, ".strtab") == 0) {
      strtab_shdr = section_hdrs[i];
    }
  }

  return;
}

void parse_symtab() {
  symnum = symtab_shdr.sh_size / sizeof(Elf32_Sym);
  symtab = (Elf32_Sym *)(elf_file + symtab_shdr.sh_offset);

  return;
}

void parse_strtab() {
  strtab = elf_file + strtab_shdr.sh_offset;

  return;
}

Elf32_Sym *get_symtab() { return symtab; }

int get_symnum() { return symnum; }

char *get_strtab() { return strtab; }
