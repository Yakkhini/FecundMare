const c = @cImport({
    @cDefine("ARCH_H", "\"arch/riscv.h\"");
    @cInclude("am.h");
    @cInclude("klib.h");
    @cInclude("klib-macros.h");
});

export fn main() void {
    zigWorkLoadEntry();
}

fn zigWorkLoadEntry() void {
    _ = c.printf("Hello, Zig Workloads!\n");
    c.halt(0);
}
