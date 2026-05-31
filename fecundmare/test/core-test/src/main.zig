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
    _ = c.printf("Starting ICache Coherence Test...\n");
    asm volatile (
        \\li a0, 0;
        \\li a1, 0x10000000;  // change UART_TX to the correct address
        \\li t1, 0x41;        // 0x41 = 'A'
        \\la a2, again;
        \\li t2, 0x00100073;  // 0x00008067 = ebreak
        \\again:
        \\sb t1, (a1);
        \\fence.i
        \\sw t2, (a2);
        \\j again;
    );
    c.halt(0);
}
