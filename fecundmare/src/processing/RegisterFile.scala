/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare

import chisel3._

import fecundmare.util.RegisterFileBundle

class RegisterFile extends Module {
  val io = IO(new RegisterFileBundle)

  val registers = RegInit(
    VecInit(Seq.fill(32)(0.U(32.W)))
  )

  val writeValid =
    io.fromProcessing.bits.writeEnable && io.fromProcessing.bits.writeAddr =/= 0.U && io.fromProcessing.valid

  for (i <- 0 until 32) {
    registers(i) := Mux(
      writeValid && io.fromProcessing.bits.writeAddr === i.U,
      io.fromProcessing.bits.writeData,
      registers(i)
    )
  }

  dontTouch(writeValid)

  io.toIDU.bits.readData1 := Mux(
    io.fromIDU.bits.readAddr1 === io.fromProcessing.bits.writeAddr && writeValid,
    io.fromProcessing.bits.writeData,
    registers(io.fromIDU.bits.readAddr1)
  )
  io.toIDU.bits.readData2 := Mux(
    io.fromIDU.bits.readAddr2 === io.fromProcessing.bits.writeAddr && writeValid,
    io.fromProcessing.bits.writeData,
    registers(io.fromIDU.bits.readAddr2)
  )

  io.toIDU.valid := true.B
  io.fromIDU.ready := true.B
  io.fromProcessing.ready := true.B
  io.fromProcessing.ready := true.B
}
