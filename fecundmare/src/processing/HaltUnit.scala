/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._

// TODO: Merge with CSR Unit into Trap Unit in future.
class HaltUnit extends Module {
  val io = IO(new Bundle {
    val reset = Input(Bool())
    val breakSignal = Input(Bool())
    val code = Input(UInt(32.W))
  })

  val halt = RegInit(false.B)
  val code = RegInit(0.U(32.W))

  halt := io.breakSignal
  code := io.code

  dontTouch(halt)
  dontTouch(code)

}
