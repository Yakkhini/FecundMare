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

_compile:
    #!/usr/bin/env zsh
    mkdir -p {{BUILD_DIR}}/bin
    VSRC=`find $NPC_CHISEL -name '*.sv' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    CSRC=`find $NPC_CHISEL -name '*.cc' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    VLTRC=`find $NPC_CHISEL -name '*.vlt' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    PERIP_SRC=`find {{PERIP_DIR}} -name '*.v' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    verilator --cc -Mdir {{BUILD_DIR}}/verilator \
    --top-module ysyxSoCFull \
    --timescale 1ns/1ns --no-timing \
    --x-assign fast --x-initial fast --noassert \
    --autoflush \
    --build -j 6 -Wno-UNUSEDSIGNAL -Wno-DECLFILENAME \
    `echo $VLTRC` `echo $CSRC` `echo $VSRC` `echo $PERIP_SRC` $YSYX_SOC_HOME/build/ysyxSoCFull \
    -I{{PERIP_DIR}}/uart16550/rtl -I{{PERIP_DIR}}/spi/rtl \
    -CFLAGS -I{{BUILD_DIR}}/verilator -CFLAGS -I{{INC_DIR}} -CFLAGS -I{{CONFIG_DIR}} -CFLAGS -g \
    -LDFLAGS -lreadline -LDFLAGS -lcapstone \
    --trace-fst --exe -o {{BUILD_DIR}}/bin/taohe


sim: (trace "Build TaoHe Simulator Program Binary.") sv _compile

trace msg:
    #!/usr/bin/env zsh
    flock {{YSYX_HOME}}/.git/ make -C {{YSYX_HOME}} .git_commit MSG='{{msg}}'
    sync

wave:
    surfer -s WaveLayout.ron {{BUILD_DIR}}/waveform.fst

fmt:
    scalafmt
    find $NPC_CHISEL -iname *.h -o -iname *.cc | xargs clang-format -i

clean:
    rm -rf out
