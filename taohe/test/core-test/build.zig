const std = @import("std");

pub fn build(b: *std.Build) void {
    const lib = b.addLibrary(.{
        .name = "core-test",
        .linkage = .static,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/main.zig"),
            .target = b.resolveTargetQuery(.{
                .cpu_arch = .riscv32,
                .cpu_features_add = std.Target.riscv.featureSet(&.{ .e, .zicsr }),
                .cpu_features_sub = std.Target.riscv.featureSet(&.{ .i, .m, .a, .f, .d, .c }),
                .os_tag = .other,
                .abi = .ilp32,
            }),
        }),
    });

    lib.addIncludePath(b.path("../../../../abstract-machine/am/include"));
    lib.addIncludePath(b.path("../../../../abstract-machine/klib/include"));

    b.installArtifact(lib);
}
