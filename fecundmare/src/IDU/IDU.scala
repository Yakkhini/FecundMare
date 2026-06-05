/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare.idu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}

import fecundmare.util.enum._
import fecundmare.util.IDUBundle
import fecundmare.util.PreSiliconPerformanceCounter

object IDUState extends ChiselEnum {
  val sWork, sSend, sWait = Value
}

class IDU extends Module {
  val io = IO(new IDUBundle)

  val state = RegInit(IDUState.sWait)

  val pc = RegInit(0.U(32.W))
  val inst = RegInit(0.U(32.W))

  pc := Mux(io.fromIFU.fire, io.fromIFU.bits.currentPC, pc)
  inst := Mux(io.fromIFU.fire, io.fromIFU.bits.inst, inst)

  io.fromIFU.ready := (state === IDUState.sWork && io.toEXU.fire) || state === IDUState.sWait
  io.toEXU.valid := state === IDUState.sWork || state === IDUState.sSend

  io.toRegisterFile.valid := true.B
  io.fromRegisterFile.ready := true.B

  switch(state) {
    is(IDUState.sWork) {
      when(!io.fromIFU.fire) {
        state := Mux(io.toEXU.fire, IDUState.sWait, IDUState.sSend)
      }
    }
    is(IDUState.sSend) {
      when(io.toEXU.fire) {
        state := IDUState.sWait
      }
    }
    is(IDUState.sWait) {
      when(io.fromIFU.fire) {
        state := IDUState.sWork
      }
    }
  }

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(inst)

  io.toEXU.bits.currentPC := pc

  val immI = inst(31) ## Fill(20, inst(31)) ## inst(30, 20)
  val immS = inst(31) ## Fill(20, inst(31)) ## inst(30, 25) ## inst(11, 7)
  val immB = inst(31) ## Fill(19, inst(31)) ## inst(7) ##
    inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
  val immU = inst(31, 12) ## 0.U(12.W)
  val immJ = inst(31) ## Fill(11, inst(31)) ## inst(19, 12) ## inst(
    20
  ) ## inst(30, 21) ## 0.U(1.W)

  val immType = decodeResult(ImmField)

  io.toEXU.bits.imm := MuxLookup(immType, 0.U)(
    Seq(
      ImmType.I.asUInt -> immI,
      ImmType.S.asUInt -> immS,
      ImmType.B.asUInt -> immB,
      ImmType.U.asUInt -> immU,
      ImmType.J.asUInt -> immJ
    )
  )

  val breakReadAddr = MuxLookup(
    inst(31, 20),
    inst(19, 15)
  )(
    Seq(
      "b000000000001".U -> 10.U
    )
  )

  io.toRegisterFile.bits.readAddr1 := MuxLookup(
    inst(6, 0),
    inst(19, 15)
  )(
    Seq(
      "b0110111".U -> 0.U,
      "b1110011".U -> breakReadAddr
    )
  )
  io.toRegisterFile.bits.readAddr2 := inst(24, 20)

  io.toEXU.bits.data1 := MuxLookup(
    decodeResult(Data1Field),
    0.U
  )(
    Seq(
      Data1Type.PC.asUInt -> pc,
      Data1Type.RS1.asUInt -> io.fromRegisterFile.bits.readData1
    )
  )

  io.toEXU.bits.data2 := MuxLookup(
    decodeResult(Data2Field),
    0.U
  )(
    Seq(
      Data2Type.IMM.asUInt -> io.toEXU.bits.imm,
      Data2Type.RS2.asUInt -> io.fromRegisterFile.bits.readData2
    )
  )

  io.toEXU.bits.registerWriteAddr := inst(11, 7)

  io.toEXU.bits.instructionType := decodeResult(InstTypeField)
  io.toEXU.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toEXU.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toEXU.bits.lsuLength := decodeResult(MemLenField)
  io.toEXU.bits.aluOp := decodeResult(ALUOpField)
  io.toEXU.bits.bjuOp := decodeResult(BJUOpField)
  io.toEXU.bits.unsigned := decodeResult(UnsignField)
  io.toEXU.bits.break := decodeResult(BreakField)

  io.toEXU.bits.lsuReadEnable := inst(6, 0) === "b0000011".U
  io.toEXU.bits.lsuWriteEnable := inst(6, 0) === "b0100011".U

  io.toEXU.bits.csrAddress := inst(31, 20)
  io.toEXU.bits.csrOperation := decodeResult(CSROPTypeField)

  val decodeSupport = Wire(Bool())
  decodeSupport := decodeResult(
    DecodeSupportField
  ) || !io.toEXU.fire
  dontTouch(decodeSupport)

  // Performance Counter
  val isJumpInst =
    decodeResult(BJUOpField) === BJUOpType.JUMP.asUInt
  val isBranchInst =
    decodeResult(
      NextPCDataTypeField
    ) === NextPCDataType.BRANCHJUMP.asUInt && !isJumpInst
  val isLoadInst = io.toEXU.bits.lsuReadEnable
  val isStoreInst = io.toEXU.bits.lsuWriteEnable
  val isArithInst =
    decodeResult(RegWriteDataTypeField) === RegWriteDataType.RESULT.asUInt &&
      (decodeResult(InstTypeField) === InstType.I.asUInt ||
        decodeResult(InstTypeField) === InstType.R.asUInt)

  PreSiliconPerformanceCounter(
    "jumpInstCounter",
    io.toEXU.fire && isJumpInst,
    32
  )
  PreSiliconPerformanceCounter(
    "branchInstCounter",
    io.toEXU.fire && isBranchInst,
    32
  )
  PreSiliconPerformanceCounter(
    "loadInstCounter",
    io.toEXU.fire && isLoadInst,
    32
  )
  PreSiliconPerformanceCounter(
    "storeInstCounter",
    io.toEXU.fire && isStoreInst,
    32
  )
  PreSiliconPerformanceCounter(
    "arithInstCounter",
    io.toEXU.fire && isArithInst,
    32
  )
}
