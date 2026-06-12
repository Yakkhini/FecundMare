---
title: FecundMare 定期进度新闻
---

## FecundMare News #2 2026/06/12

欢迎了解 **丰富海处理器（FecundMare）** 的开发进度。本周的工作主要面向  RISC-V M 扩展集下乘除法指令的支持，带来一系列的探索和变动。

### 微架构变动 - Microarchitecture

1. 基于 [chipsalliance/rvdecoderdb](https://github.com/chipsalliance/rvdecoderdb) 重构了 IDU 中译码信号的控制逻辑，现在译码模块的可拓展性和可维护性更强；
2. 进一步解耦各功能单元的数据通路，并通过统一的控制信号来拆分，移除了译码结果中一些可以省去的信号；
3. InstructionProcessing 顶层中，不再仅仅按照是否遇到 Load / Store 指令决定是否进入等待状态，而是重构成多个功能单元都可触发的 `sWaitUnit` 状态；
4. 探索并基于 sequencer 维护的 [硬件计算库](https://github.com/sequencer/arithmetic) 的实现接入了 Radix-4 Booth's Recoding Wallace Tree 乘法器和 SRT4 除法器，撰写了包括原理阐述、工程思路、性能分析的 [[2026-06-08-muldivinst|博客文章]]。

### 基础设施 - Infrastructure

1. 在本地和 CI 中初始化构建所需的第三方依赖库；
2. 修改负载的构建目标从 RV32E 到 RV32IM；
3. 处理新增的性能计数器数据并打印到性能报告中。

### 性能评估 - Performance

本周结束时，性能结果如下：

* IPC: 0.13
* Frequency: 529.42MHz
* Area: 60587.52 $um^2$

由于实现了乘除法器，频率、面积均有衰退，同时大量计算指令的节省也让 IPC 更低的仿存指令占比更高，拉低了整体的 IPC 表现。但是在 M 扩展的支持下，总周期数和总指令数都有非常显著的下降：

* Total Cycles: 915653072 -> 502654344, 45% decreasing;
* Total Instructions: 201561722 -> 66669545, 67% decreasing.

或许在后面的性能报告中可以考虑更准确的评估和汇报方式？目前有点想把标准性能测试从 MicroBench 迁移到 [ArchBench](https://github.com/OSCPU/archbench/tree/dev) 上。

## FecundMare News #1 2026/06/05

欢迎了解丰富海处理器的开发进度。本周 **丰富海处理器（FecundMare）** 成功诞生，脱胎于旧的 **桃河处理器（TaoHe）**，并立即进行了紧锣密鼓的重构与开发。

### 微架构变动 - Microarchitecture

1. 重新将 TaoHe 中为了流片约束砍的 ICache 规格恢复；
2. 恢复 32 个寄存器数量以支持 RV32I（~~虽然好像负载构建目标还没改过来~~）；
3. 处理器顶层拆分为 **InstructionDelivery** 和 **InstructionProcessing** 两个部分，即前后端解耦。这个命名风格参考了 [苹果的文档](https://developer.apple.com/download/apple-silicon-cpu-optimization-guide/) 中对微架构的描述，前者包含 IFU、IDU、ICache 并包装为一个模块，后者主体由原 EXU 构成，并放入 RegisterFile、CSR 等模块；
4. 拆解部分 Processing 部分中的逻辑到单独的 ALU 和 BJU 中，作为多数据通路的雏形，并相应修改影响到的 IDU 逻辑；
5. 优化了寄存器堆写回的时序。

虽然整体看起来产生了大范围的重构，但其实相当一部分设计和原先是等价的。可能最大的不同只是单独让 JUMP 指令不再依赖 ALU 的相关变更。目前的主要思路是推进微架构指令供给和执行的解耦与数据通路的解耦，为未来迭代作准备。

### 基础设施 - Infrastructure

1. 搭建了自动化追踪时序与性能的 CI，不用再像之前一样每次改动完手动测试，然后隔一段时间统一 Commit 到仓库中了。恭喜 FecundMare 在持续集成道路上迈出的第一步；
2. 优化了设计生成器中参数化的框架，从原来需要手动一层层传变成了通过 `FMConfig` `FMModule` `FMBundle` 与隐式传参功能来参数化；
3. 使用 **木兰宽松许可证，第二版** 作为项目主要设计的开源许可，**Creative Commons CC-BY-SA 4.0** 作为文档规范，并通过社区的 pre-commit 和 reuse 等工具来协助仓库适配 SPDX REUSE 等开源规范，增强了开源合规水平。

### 性能评估 - Performance Evaluation

FecundMare 的 CI 性能评估数据可以在 [仓库的 `perf-results` 分支](https://github.com/Yakkhini/FecundMare/tree/perf-results) 中找到。

作为 Baseline，针对 FecundMare 的第一次性能评估结果如下：

* IPC: 0.24
* Frequency: 653MHz
* Area: 42551 $um^2$

本周结束时，性能结果如下：

* IPC: 0.22
* Frequency: 532MHz
* Area: 47344 $um^2$

恭喜 FecundMare 在 PPA 上全面倒退，无愧于我低性能之父的名号！尽管如此，还是可以辩解一下。IPC 的主要性能回退来自于更新交叉编译工具链后，实时编译的负载程序中可能指令踪迹对 I$ 产生了性能衰退，频率和面积变差则是因为寄存器数量的增加。

总的来看，FecundMare 开了一个好头，进一步提升了未来的 PPA 提升空间。让我们期待它未来的变化吧！