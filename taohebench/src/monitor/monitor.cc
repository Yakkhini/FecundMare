#include <common.h>
#include <cpu/difftest.h>
#include <cpu/ftrace.h>
#include <getopt.h>
#include <memory/paddr.h>
#include <memory/vaddr.h>
#include <sdb.h>

static char *diff_so_file =
    strcat(getenv("NEMU_HOME"), "/build/riscv32-nemu-interpreter-so");
static char *img_file = NULL;

static uint32_t DEFAULT_MEM[] = {
    0x3e800093, 0x3e810093, 0x3e810193, 0x7d018213, 0x3e820293,
    0x3e828313, 0x7d008113, 0x00430313, 0x3e800093, 0x3e810093,
    0x3e810193, 0x7d018213, 0x3e820293, 0x3e828313, 0x7d008113,
    0x00430313, 0x3e800093, 0x3e810093, 0x3e810193, 0x7d018213,
    0x3e820293, 0x3e828313, 0x7d008113, 0x00430313, 0x00100073};

long load_img() {
  if (img_file == NULL) {
    Log("No image is given. Use the default build-in image.");
    memcpy(guest_to_host(0x80000000), DEFAULT_MEM, sizeof(uint32_t) * 25);
    return 4096; // built-in image size
  }

  FILE *fp = fopen(img_file, "rb");

  fseek(fp, 0, SEEK_END);
  long size = ftell(fp);

  Log("The image is %s, size = %ld", img_file, size);

  fseek(fp, 0, SEEK_SET);
  int ret = fread(guest_to_host(0x80000000), size, 1, fp);
  assert(ret == 1);

  fclose(fp);
  return size;
}

void parse_args(int argc, char *argv[]) {
  const struct option table[] = {
      {"batch", no_argument, NULL, 'b'},
      {"ftrace", required_argument, NULL, 'f'},
      {0, 0, NULL, 0},
  };
  int o;
  while ((o = getopt_long(argc, argv, "-bhf:", table, NULL)) != -1) {
    switch (o) {
    case 'b':
      batch_mode_enable();
      break;
    case 'f':
      ftrace_init(optarg);
      break;
    case 1:
      img_file = optarg;
      return;
    default:
      printf("Usage: %s [OPTION...] IMAGE [args]\n\n", argv[0]);
      printf("\t-b,--batch              run with batch mode\n");
      printf("\t-f,--ftrace=FILE        trace functions with elf file.\n");
      printf("\t-l,--log=FILE           output log to FILE\n");
      printf("\t-d,--diff=REF_SO        run DiffTest with reference REF_SO\n");
      printf("\t-p,--port=PORT          run DiffTest with port PORT\n");
      printf("\n");
      exit(0);
    }
  }
  return;
}

void welcome() {
  printf(ANSI_FG_BLUE "\
   _   _ _   _   _                _____           _   _      _  \n\
  | \\ | (_) | | | |              |_   _|         | | | |    | | \n\
  |  \\| |_  | |_| | __ _  ___      | | __ _  ___ | |_| | ___| | \n\
  | . ` | | |  _  |/ _` |/ _ \\     | |/ _` |/ _ \\|  _  |/ _ \\ | \n\
  | |\\  | | | | | | (_| | (_) |    | | (_| | (_) | | | |  __/_| \n\
  \\_| \\_/_| \\_| |_/\\__,_|\\___( )   \\_/\\__,_|\\___/\\_| |_/\\___(_) \n\
                             |/\n\n" ANSI_NONE);
}

void monitor_init(int argc, char **argv) {
  welcome();

  parse_args(argc, argv);
  long img_size = load_img();

#if CONFIG_BATCH_MODE == true
  batch_mode_enable();
#endif

#if CONFIG_DIFFTEST == true
  difftest_init(diff_so_file, img_size, 1234);
#endif

  init_regex();
  init_wp_pool();
  flash_init();
}
