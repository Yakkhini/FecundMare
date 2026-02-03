package taohe.idu

import chisel3._
import chisel3.util.Fill
import chisel3.util.MuxLookup
import chisel3.util.{switch, is}

import taohe.util.enum._
import taohe.util.IDUBundle
import taohe.util.PerformanceCounter

object IDUState extends ChiselEnum {
  val sIdle, sSend = Value
}

class IDU extends Module {
  val io = IO(new IDUBundle)

  val state = RegInit(IDUState.sIdle)

  val pcRegister = RegInit(0.U(32.W))
  val instRegister = RegInit(0.U(32.W))

  io.fromIFU.ready := state === IDUState.sIdle || io.toEXU.fire
  pcRegister := Mux(io.fromIFU.fire, io.fromIFU.bits.currentPC, pcRegister)
  instRegister := Mux(io.fromIFU.fire, io.fromIFU.bits.inst, instRegister)
  val pc = Mux(io.fromIFU.fire, io.fromIFU.bits.currentPC, pcRegister)
  val inst = Mux(io.fromIFU.fire, io.fromIFU.bits.inst, instRegister)

  io.toEXU.valid := state === IDUState.sSend
  io.toRegisterFile.valid := true.B
  io.fromRegisterFile.ready := true.B

  switch(state) {
    is(IDUState.sIdle) {
      when(io.fromIFU.fire) {
        state := IDUState.sSend
      }
    }
    is(IDUState.sSend) {
      when(io.toEXU.fire) {
        state := IDUState.sIdle
      }
    }
  }

  import IDUTable.decodeTable

  val decodeResult = decodeTable.decode(inst)

  io.toIFU.normalNextPC := decodeResult(NextPCDataTypeField) ===
    NextPCDataType.NORMAL.asUInt

  io.toEXU.bits.currentPC := pc

  val imm_i = inst(31) ## Fill(20, inst(31)) ## inst(30, 20)
  val imm_s = inst(31) ## Fill(20, inst(31)) ## inst(30, 25) ## inst(11, 7)
  val imm_b = inst(31) ## Fill(19, inst(31)) ## inst(7) ##
    inst(30, 25) ## inst(11, 8) ## 0.U(1.W)
  val imm_u = inst(31, 12) ## 0.U(12.W)
  val imm_j = inst(31) ## Fill(11, inst(31)) ## inst(19, 12) ## inst(
    20
  ) ## inst(30, 21) ## 0.U(1.W)

  val immType = decodeResult(ImmField)

  io.toEXU.bits.imm := MuxLookup(immType, 0.U)(
    Seq(
      ImmType.I.asUInt -> imm_i,
      ImmType.S.asUInt -> imm_s,
      ImmType.B.asUInt -> imm_b,
      ImmType.U.asUInt -> imm_u,
      ImmType.J.asUInt -> imm_j
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

  io.toEXU.bits.registerReadData1 := io.fromRegisterFile.bits.readData1
  io.toEXU.bits.registerReadData2 := io.fromRegisterFile.bits.readData2
  io.toEXU.bits.registerWriteAddr := inst(11, 7)

  io.toEXU.bits.instructionType := decodeResult(InstTypeField)
  io.toEXU.bits.data1Type := decodeResult(Data1Field)
  io.toEXU.bits.data2Type := decodeResult(Data2Field)
  io.toEXU.bits.registerWriteType := decodeResult(
    RegWriteDataTypeField
  )
  io.toEXU.bits.nextPCType := decodeResult(NextPCDataTypeField)
  io.toEXU.bits.lsuLength := decodeResult(MemLenField)
  io.toEXU.bits.aluOp := decodeResult(ALUOpField)
  io.toEXU.bits.compareOp := decodeResult(CompareOpField)
  io.toEXU.bits.unsigned := decodeResult(UnsignField)
  io.toEXU.bits.break := decodeResult(BreakField)

  io.toEXU.bits.lsuReadEnable := inst(6, 0) === "b0000011".U
  io.toEXU.bits.lsuWriteEnable := inst(6, 0) === "b0100011".U

  io.toEXU.bits.csrAddress := inst(31, 20)
  io.toEXU.bits.csrOperation := decodeResult(CSROPTypeField)

  val decodeSupport = Wire(Bool())
  decodeSupport := decodeResult(DecodeSupportField) | ~io.fromIFU.valid
  dontTouch(decodeSupport)

  // Performance Counter
  val isJumpInst =
    decodeResult(NextPCDataTypeField) === NextPCDataType.RESULT.asUInt
  val isBranchInst =
    decodeResult(NextPCDataTypeField) === NextPCDataType.BRANCH.asUInt
  val isLoadInst = io.toEXU.bits.lsuReadEnable
  val isStoreInst = io.toEXU.bits.lsuWriteEnable
  val isArithInst =
    decodeResult(RegWriteDataTypeField) === RegWriteDataType.RESULT.asUInt &&
      (decodeResult(InstTypeField) === InstType.I.asUInt ||
        decodeResult(InstTypeField) === InstType.R.asUInt)

  val jumpInstCycleCounter = PerformanceCounter(isJumpInst, 32)
  val jumpInstCounter = PerformanceCounter(
    io.toEXU.fire && isJumpInst,
    32
  )
  val branchInstCycleCounter = PerformanceCounter(isBranchInst, 32)
  val branchInstCounter = PerformanceCounter(
    io.toEXU.fire && isBranchInst,
    32
  )
  val loadInstCycleCounter = PerformanceCounter(isLoadInst, 32)
  val loadInstCounter = PerformanceCounter(
    io.toEXU.fire && isLoadInst,
    32
  )
  val storeInstCycleCounter = PerformanceCounter(isStoreInst, 32)
  val storeInstCounter = PerformanceCounter(
    io.toEXU.fire && isStoreInst,
    32
  )
  val arithInstCycleCounter = PerformanceCounter(isArithInst, 32)
  val arithInstCounter = PerformanceCounter(
    io.toEXU.fire && isArithInst,
    32
  )
}
