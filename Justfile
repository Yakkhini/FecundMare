set dotenv-load

BUILD_DIR := "$NPC_CHISEL/out"
INC_DIR := "$NPC_CHISEL/include"
CONFIG_DIR := "$NPC_CHISEL/config"
NVBOARD_ARCHIVE := "$NVBOARD_HOME/build/nvboard.a"
NPC_NAME := "TaoHe"

verilog:
    #!/usr/bin/env zsh
    mill -i build.run
    echo "SystemVerilog files are generated."

_compile:
    #!/usr/bin/env zsh
    mkdir -p {{BUILD_DIR}}/bin
    VSRC=`find $NPC_CHISEL -name '*.sv' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    CSRC=`find $NPC_CHISEL -name '*.cc' | tr '\n' ' '` # we use echo command latter cause dollar var will cause error
    verilator --cc -Mdir {{BUILD_DIR}}/verilator \
    --top-module {{NPC_NAME}} \
    --build -Wall -Wno-UNUSEDSIGNAL -Wno-DECLFILENAME `echo $CSRC` `echo $VSRC` \
    -CFLAGS -I{{BUILD_DIR}}/verilator -CFLAGS -I{{INC_DIR}} -CFLAGS -I{{CONFIG_DIR}} \
    -LDFLAGS -lreadline -LDFLAGS -lcapstone \
    --trace --exe -o {{BUILD_DIR}}/bin/{{NPC_NAME}}


sim: verilog _compile

nju lab:
    just _common nju{{lab}}

fmt:
    scalafmt
    find $NPC_CHISEL -iname *.h -o -iname *.cc | xargs clang-format -i

clean:
    rm -rf out
