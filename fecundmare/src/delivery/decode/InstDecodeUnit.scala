/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare.decode

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}
import chisel3.experimental.noPrefix

import fecundmare.util.enum._
import fecundmare.util.IDUBundle
import fecundmare.util.PreSiliconPerformanceCounter

object IDUState extends ChiselEnum {
  val sWork, sSend, sWait = Value
}

class InstDecodeUnit extends Module {
  val io = IO(new IDUBundle)

  val state = RegInit(IDUState.sWait)

  val pc = RegInit(0.U(32.W))
  val inst = RegInit(0.U(32.W))

  pc := Mux(io.fromIFU.fire, io.fromIFU.bits.currentPC, pc)
  inst := Mux(io.fromIFU.fire, io.fromIFU.bits.inst, inst)

  io.fromIFU.ready := (state === IDUState.sWork && io.toProcessing.fire) || state === IDUState.sWait
  io.toProcessing.valid := state === IDUState.sWork || state === IDUState.sSend

  io.toRegisterFile.valid := true.B
  io.fromRegisterFile.ready := true.B

  switch(state) {
    is(IDUState.sWork) {
      when(!io.fromIFU.fire) {
        state := Mux(io.toProcessing.fire, IDUState.sWait, IDUState.sSend)
      }
    }
    is(IDUState.sSend) {
      when(io.toProcessing.fire) {
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

  val decodeSupport = noPrefix {
    Wire(Bool()).suggestName("decodeSupport")
  }
  val decodeResult = decodeTable.decode(inst)

  io.toProcessing.bits.currentPC := pc

  io.toProcessing.bits.funcType := decodeResult(FuncTypeField)
  io.toProcessing.bits.funcOpType := decodeResult(FuncOpTypeField)

  val immI = inst(31) ## Fill(20, inst(31)) ## inst(30, 20)
  val immS = inst(31) ## Fill(20, inst(31)) ## inst(30, 25) ## inst(11, 7)
  val immB = inst(31) ## Fill(19, inst(31)) ## inst(7) ##
    inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
  val immU = inst(31, 12) ## 0.U(12.W)
  val immJ = inst(31) ## Fill(11, inst(31)) ## inst(19, 12) ## inst(
    20
  ) ## inst(30, 21) ## 0.U(1.W)

  val immType = decodeResult(ImmField)

  io.toProcessing.bits.imm := MuxLookup(immType, 0.U)(
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

  io.toProcessing.bits.data1 := MuxLookup(
    decodeResult(Data1Field),
    0.U
  )(
    Seq(
      Data1Type.PC.asUInt -> pc,
      Data1Type.RS1.asUInt -> io.fromRegisterFile.bits.readData1
    )
  )

  io.toProcessing.bits.data2 := MuxLookup(
    decodeResult(Data2Field),
    0.U
  )(
    Seq(
      Data2Type.IMM.asUInt -> io.toProcessing.bits.imm,
      Data2Type.RS2.asUInt -> io.fromRegisterFile.bits.readData2
    )
  )

  io.toProcessing.bits.registerWriteAddr := inst(11, 7)

  io.toProcessing.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toProcessing.bits.registerWriteEnable := decodeResult(RegWriteEnableField)
  io.toProcessing.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toProcessing.bits.lsuLength := decodeResult(MemLenField)
  io.toProcessing.bits.unsigned := decodeResult(UnsignField)
  io.toProcessing.bits.break := decodeResult(BreakField)

  io.toProcessing.bits.lsuReadEnable := inst(6, 0) === "b0000011".U
  io.toProcessing.bits.lsuWriteEnable := inst(6, 0) === "b0100011".U

  io.toProcessing.bits.csrAddress := inst(31, 20)
  io.toProcessing.bits.csrOperation := decodeResult(CSROPTypeField)

  decodeSupport := decodeResult(
    DecodeSupportField
  ) || !io.toProcessing.fire
  dontTouch(decodeSupport)

  // Performance Counter
  val isArithInst = decodeResult(FuncTypeField) === FuncType.ALU.asUInt

  val isBranchJumpInst = decodeResult(FuncTypeField) === FuncType.BJU.asUInt
  val isJumpOpType = decodeResult(FuncOpTypeField) === BJUOpType.JUMP.asUInt
  val isJumpInst = isBranchJumpInst && isJumpOpType
  val isBranchInst = isBranchJumpInst && !isJumpOpType

  val isLoadInst = io.toProcessing.bits.lsuReadEnable
  val isStoreInst = io.toProcessing.bits.lsuWriteEnable

  PreSiliconPerformanceCounter(
    "arithInstCounter",
    io.toProcessing.fire && isArithInst,
    32
  )
  PreSiliconPerformanceCounter(
    "jumpInstCounter",
    io.toProcessing.fire && isJumpInst,
    32
  )
  PreSiliconPerformanceCounter(
    "branchInstCounter",
    io.toProcessing.fire && isBranchInst,
    32
  )
  PreSiliconPerformanceCounter(
    "loadInstCounter",
    io.toProcessing.fire && isLoadInst,
    32
  )
  PreSiliconPerformanceCounter(
    "storeInstCounter",
    io.toProcessing.fire && isStoreInst,
    32
  )
}
