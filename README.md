# FecundMare

The successor of TaoHe 3-stage pipelined RISC-V 32E processor core. Going to support RISC-V 32IM with superscalar Out-of-Order microarchitecture in future. Currently still in heavy development.

## Integration & Simulation

This design is tested in [One Student One Chip Project](https://ysyx.oscc.cc/) educational environment. It can be easily run with a completed abstract-machine running environment.

## Performance Evaluation

There is an automatic performance evaluation on CI, which would be triggered on every Git push. The evaluation results includes IPC on Microbench train scale workload and frequency & area information via [yosys-sta](https://github.com/OSCPU/yosys-sta) tool, on [ICsprout 55nm Open Source PDK](https://github.com/openecos-projects/icsprout55-pdk).

Check out the `perf-results` branch to find archived CI output.

## License

This repository is licensed on Mulan Permissive Software License, Version 2.

```
Copyright (c) 2026 Yakkhini Yaksiscc@gmail.com
FecundMare is licensed under Mulan PSL v2.
You can use this software according to the terms and conditions of the Mulan
PSL v2.
You may obtain a copy of Mulan PSL v2 at:
         http://license.coscl.org.cn/MulanPSL2
THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY
KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
See the Mulan PSL v2 for more details.
```
