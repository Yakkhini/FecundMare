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
    echo "SystemVerilog files are generated."

soc-sv:
    make -C $YSYX_SOC_HOME verilog

_rmdb:
    rm -f $NPC_CHISEL/out/mtrace.db

_compile target:
    #!/usr/bin/env zsh
    mkdir -p {{BUILD_DIR}}/bin
    mkdir -p {{BUILD_DIR}}/verilator
    VSRC=`find {{BUILD_DIR}}/verilog -name '*.sv' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    CSRC=`find $NPC_CHISEL -name '*.cc' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    VLTRC=`find $NPC_CHISEL -name '*.vlt' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    PERIP_SRC=`find {{PERIP_DIR}} -name '*.v' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    verilator --cc -Mdir {{BUILD_DIR}}/verilator/{{target}} \
    --top-module {{target}} \
    --timescale 1ns/1ns --no-timing \
    -O3 --x-assign fast --x-initial fast --noassert \
    --autoflush \
    -Wno-UNUSEDSIGNAL -Wno-DECLFILENAME -Wno-UNOPTFLAT \
    `echo $VLTRC` `echo $CSRC` `echo $VSRC` `echo $PERIP_SRC` $YSYX_SOC_HOME/build/ysyxSoCFull {{NVBOARD_ARCHIVE}} \
    -I{{PERIP_DIR}}/uart16550/rtl -I{{PERIP_DIR}}/spi/rtl \
    -I{{BUILD_DIR}}/verilog/verification \
    -CFLAGS -I{{BUILD_DIR}}/verilator -CFLAGS -I{{INC_DIR}} -CFLAGS -I{{CONFIG_DIR}} -CFLAGS -g \
    -CFLAGS -O3 -CFLAGS -flto -LDFLAGS -flto \
    -CFLAGS -DCONFIG_TARGET_{{target}} \
    -LDFLAGS -lreadline -LDFLAGS -lcapstone -LDFLAGS -lSDL2 -LDFLAGS -lSDL2_image -LDFLAGS -lSDL2_ttf \
    -LDFLAGS -lsqlite3 \
    --trace-fst --exe -o {{BUILD_DIR}}/bin/taohe
    make -C {{BUILD_DIR}}/verilator/{{target}} -f V{{target}}.mk -j $NIX_BUILD_CORES AR=gcc-ar


soc-sim: (trace "Build TaoHe Simulator Program Binary in Full SoC Mode.") sv sta _rmdb (_compile "ysyxSoCFull")

core-sim: (trace "Build TaoHe Simulator Program Binary in Single Core Mode.") sv sta _rmdb (_compile "TaoHe")

micro-sim:
  cd $NPC_CHISEL/micro-sim && cargo build --release && cargo build

sta:
    #!/usr/bin/env nu
    mut update = true
    if (($env.NPC_CHISEL + /out/perf.toml) | path exists) {
      if ((cat ($env.NPC_CHISEL + /out/perf.toml) | from toml | get TaoHe | get commit) == (git rev-parse --short HEAD)) {
        $update = false
      }
    }
    if (not ($env.NPC_CHISEL + /out/sta/filelist.f | path exists)) {
      just sv
    }
    if $update {
      make --silent -C $env.YOSYS_STA_HOME sta out+err> ($env.NPC_CHISEL + /out/sta/sta.log)
      let freq = (head -n 6 ($env.YOSYS_STA_HOME + /result/taohe__TaoHe-500MHz/taohe__TaoHe.rpt) | tail -n 1 | awk '{print $(NF-1)}' | into float)
      let area = (tail -n 3 ($env.YOSYS_STA_HOME + /result/taohe__TaoHe-500MHz/synth_stat.txt) | head -n 1 | awk '{print $NF}' | into float)
      let time = (date now | format date "%Y-%m-%d %H:%M:%S")
      let commit = (git rev-parse --short HEAD)
      let message = (git log -1 --pretty=%B | str trim)
      {"TaoHe": {"freq(MHz)": $freq, "area(um^2)": $area, "commit": $commit, "message": $message "time": $time}} | to toml | save -f ($env.NPC_CHISEL + /out/perf.toml)
    }

formal: sv
    sby -f -d ./out/formal/result ./formal/ICache.sby

perf:
    #!/usr/bin/env nu
    print "Performance data:"
    cat ($env.NPC_CHISEL + /out/perf.toml) | from toml

archive-perf: clean sta
    #!/usr/bin/env nu
    cd ($env.NPC_CHISEL + /../am-kernels/benchmarks/microbench)
    make ARCH=riscv32e-ysyxsoc run mainargs=train
    cd $env.NPC_CHISEL
    let index = (ls ($env.NPC_CHISEL + /perf-archive) | length)
    let commit = (git rev-parse --short HEAD)
    cp ($env.NPC_CHISEL + /out/perf.toml) ($env.NPC_CHISEL + /perf-archive/taohe-perf-($index)-($commit).toml)

trace msg:
    #!/usr/bin/env zsh
    flock {{YSYX_HOME}}/.git/ make -C {{YSYX_HOME}} .git_commit MSG='{{msg}}'
    sync

wave:
    surfer -s WaveLayout.ron {{BUILD_DIR}}/waveform.fst

mtrace-view:
    #!/usr/bin/env nu
    if (($env.NPC_CHISEL + /out/mtrace.db) | path exists) {
      julia --project=MemoryAnalyzer -m MemoryAnalyzer
      okular ./out/memory_heatmap.png
    }

fmt:
    scalafmt
    fd -e h -e cc -e c -a | xargs clang-format -i
    cd micro-sim && cargo fmt -v && taplo fmt Cargo.toml

clean:
    rm -rf out
    cd micro-sim && cargo clean
