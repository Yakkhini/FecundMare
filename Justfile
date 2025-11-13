set dotenv-load

BUILD_DIR := "$NPC_CHISEL/out"
INC_DIR := "$NPC_CHISEL/taohebench/include"
CONFIG_DIR := "$NPC_CHISEL/taohebench/config"
NVBOARD_ARCHIVE := "$NVBOARD_HOME/build/nvboard.a"
PERIP_DIR := "$YSYX_SOC_HOME/perip"
YSYX_HOME := "$NEMU_HOME/.."
NPC_NAME := "TaoHe"

sv:
    #!/usr/bin/env zsh
    mill -i taohe.run
    rm -r {{BUILD_DIR}}/sta/verification
    echo "SystemVerilog files are generated."

soc-sv:
    make -C $YSYX_SOC_HOME verilog

_compile:
    #!/usr/bin/env zsh
    mkdir -p {{BUILD_DIR}}/bin
    VSRC=`find {{BUILD_DIR}}/verilog -name '*.sv' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    CSRC=`find $NPC_CHISEL -name '*.cc' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    VLTRC=`find $NPC_CHISEL -name '*.vlt' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    PERIP_SRC=`find {{PERIP_DIR}} -name '*.v' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    verilator --cc -Mdir {{BUILD_DIR}}/verilator \
    --top-module ysyxSoCFull \
    --timescale 1ns/1ns --no-timing \
    -O3 --x-assign fast --x-initial fast --noassert \
    --autoflush \
    -Wno-UNUSEDSIGNAL -Wno-DECLFILENAME -Wno-UNOPTFLAT \
    `echo $VLTRC` `echo $CSRC` `echo $VSRC` `echo $PERIP_SRC` $YSYX_SOC_HOME/build/ysyxSoCFull {{NVBOARD_ARCHIVE}} \
    -I{{PERIP_DIR}}/uart16550/rtl -I{{PERIP_DIR}}/spi/rtl \
    -I{{BUILD_DIR}}/verilog/verification \
    -CFLAGS -I{{BUILD_DIR}}/verilator -CFLAGS -I{{INC_DIR}} -CFLAGS -I{{CONFIG_DIR}} -CFLAGS -g \
    -CFLAGS -O3 -CFLAGS -flto -LDFLAGS -flto \
    -LDFLAGS -lreadline -LDFLAGS -lcapstone -LDFLAGS -lSDL2 -LDFLAGS -lSDL2_image -LDFLAGS -lSDL2_ttf \
    --trace-fst --exe -o {{BUILD_DIR}}/bin/taohe
    make -C {{BUILD_DIR}}/verilator -f VysyxSoCFull.mk -j $NIX_BUILD_CORES AR=gcc-ar


sim: (trace "Build TaoHe Simulator Program Binary.") sv perf _compile

_sta: sv
  make --silent -C $YOSYS_STA_HOME sta > {{BUILD_DIR}}/sta/sta.log

perf:
    #!/usr/bin/env nu
    mut update = true
    if (($env.NPC_CHISEL + /out/perf.toml) | path exists) {
      if ((cat ($env.NPC_CHISEL + /out/perf.toml) | from toml | get TaoHe | get commit) == (git rev-parse --short HEAD)) {
        $update = false
      }
    }
    if $update {
      just _sta
      let freq = (head -n 5 ($env.YOSYS_STA_HOME + /result/taohe__TaoHe-500MHz/taohe__TaoHe.rpt) | tail -n 1 | awk '{print $(NF-1)}' | into float)
      let area = (tail -n 3 ($env.YOSYS_STA_HOME + /result/taohe__TaoHe-500MHz/synth_stat.txt) | head -n 1 | awk '{print $NF}' | into float)
      let time = (date now | format date "%Y-%m-%d %H:%M:%S")
      let commit = (git rev-parse --short HEAD)
      {"TaoHe": {"freq(MHz)": $freq, "area(um^2)": $area, "commit": $commit, "time": $time}} | to toml | save -f ($env.NPC_CHISEL + /out/perf.toml)
    }
    print "Performance data in current commit:"
    cat ($env.NPC_CHISEL + /out/perf.toml) | from toml

trace msg:
    #!/usr/bin/env zsh
    flock {{YSYX_HOME}}/.git/ make -C {{YSYX_HOME}} .git_commit MSG='{{msg}}'
    sync

wave:
    surfer -s WaveLayout.ron {{BUILD_DIR}}/waveform.fst

fmt:
    scalafmt
    find $NPC_CHISEL -iname *.h -o -iname *.cc -o -iname *.c | xargs clang-format -i

clean:
    rm -rf out
