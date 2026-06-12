/*
 * SPDX-FileCopyrightText: 2026 Yakkhini <Yaksiscc@gmail.com>
 *
 * SPDX-License-Identifier: MulanPSL-2.0
 */

package fecundmare.decode

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode.DecodePattern
import chisel3.util.experimental.decode.DecodeField
import chisel3.util.experimental.decode.DecodeTable
import chisel3.util.experimental.decode.BoolDecodeField

import org.chipsalliance.rvdecoderdb.Instruction

import fecundmare.util.enum._

case class InstructionPattern(instruction: Instruction) extends DecodePattern {
  def bitPat: BitPat = BitPat(s"b${instruction.encoding.toString}")

  val name = instruction.name
  val args = instruction.args.map(_.name)

  def hasArg(arg: String): Boolean = args.contains(arg)
}

object ImmField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "immType"
  def chiselType = UInt(ImmType.getWidth.W)

  def genTable(op: InstructionPattern): BitPat = {
    val immType =
      if (op.hasArg("jimm20")) ImmType.J
      else if (op.hasArg("imm20")) ImmType.U
      else if (op.hasArg("bimm12lo") || op.hasArg("bimm12hi")) ImmType.B
      else if (op.hasArg("imm12lo") || op.hasArg("imm12hi")) ImmType.S
      else ImmType.I

    BitPat(immType.litValue.U(ImmType.getWidth.W))
  }

}

object FuncTypeField extends DecodeField[InstructionPattern, UInt] {
  def name = "funcType"
  def chiselType = UInt(FuncType.getWidth.W)
  def genTable(op: InstructionPattern): BitPat = {
    val t = op.name match {
      case "lui" | "auipc" | "add" | "addi" | "sub" | "sll" | "slli" | "slt" |
          "slti" | "sltu" | "sltiu" | "xor" | "xori" | "srl" | "srli" | "sra" |
          "srai" | "or" | "ori" | "and" | "andi" =>
        FuncType.ALU
      case "jal" | "jalr" | "beq" | "bne" | "blt" | "bge" | "bltu" | "bgeu" =>
        FuncType.BJU
      case "mul" | "mulh" | "mulhsu" | "mulhu" =>
        FuncType.MUL
      case "div" | "divu" | "rem" | "remu" =>
        FuncType.DIV
      case "lb" | "lh" | "lw" | "lbu" | "lhu" | "sb" | "sh" | "sw" =>
        FuncType.MEM
      case "csrrw" | "csrrs" | "ecall" | "mret" =>
        FuncType.CSR
      case _ =>
        FuncType.NONE
    }
    BitPat(t.litValue.U(FuncType.getWidth.W))
  }
}

object FuncOpTypeField extends DecodeField[InstructionPattern, UInt] {
  def name = "funcOpType"
  def chiselType = UInt(FuncOpType.width.W)
  def genTable(op: InstructionPattern): BitPat = {
    val opType = op.name match {
      case "lui" | "auipc" | "add" | "addi" => ALUOpType.ADD.litValue
      case "sub"                            => ALUOpType.SUB.litValue
      case "sll" | "slli"                   => ALUOpType.SLL.litValue
      case "slt" | "slti"                   => ALUOpType.SLT.litValue
      case "sltu" | "sltiu"                 => ALUOpType.SLTU.litValue
      case "xor" | "xori"                   => ALUOpType.XOR.litValue
      case "srl" | "srli"                   => ALUOpType.SRL.litValue
      case "sra" | "srai"                   => ALUOpType.SRA.litValue
      case "or" | "ori"                     => ALUOpType.OR.litValue
      case "and" | "andi"                   => ALUOpType.AND.litValue
      case "jal" | "jalr"                   => BJUOpType.JUMP.litValue
      case "beq"                            => BJUOpType.EQ.litValue
      case "bne"                            => BJUOpType.NE.litValue
      case "blt"                            => BJUOpType.LT.litValue
      case "bge"                            => BJUOpType.GE.litValue
      case "bltu"                           => BJUOpType.LTU.litValue
      case "bgeu"                           => BJUOpType.GEU.litValue
      case "mul"                            => MULOpType.MUL.litValue
      case "mulh"                           => MULOpType.MULH.litValue
      case "mulhsu"                         => MULOpType.MULHSU.litValue
      case "mulhu"                          => MULOpType.MULHU.litValue
      case "div"                            => DIVOpType.DIV.litValue
      case "divu"                           => DIVOpType.DIVU.litValue
      case "rem"                            => DIVOpType.REM.litValue
      case "remu"                           => DIVOpType.REMU.litValue
      case _ => return BitPat.dontCare(FuncOpType.width)
    }
    BitPat(opType.U(FuncOpType.width.W))
  }
}

object ALUOpField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "aluOpType"
  def chiselType = UInt(ALUOpType.getWidth.W)

  def genTable(op: InstructionPattern): BitPat = {
    val aluOp = op.name match {
      case "lui" | "auipc" | "add" | "addi" => ALUOpType.ADD
      case "sub"                            => ALUOpType.SUB
      case "sll" | "slli"                   => ALUOpType.SLL
      case "slt" | "slti"                   => ALUOpType.SLT
      case "sltu" | "sltiu"                 => ALUOpType.SLTU
      case "xor" | "xori"                   => ALUOpType.XOR
      case "srl" | "srli"                   => ALUOpType.SRL
      case "sra" | "srai"                   => ALUOpType.SRA
      case "or" | "ori"                     => ALUOpType.OR
      case "and" | "andi"                   => ALUOpType.AND
      case _ => return BitPat.dontCare(ALUOpType.getWidth)
    }

    BitPat(aluOp.litValue.U(ALUOpType.getWidth.W))
  }
}

object BJUOpField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "bjuOpType"
  def chiselType = UInt(BJUOpType.getWidth.W)

  def genTable(op: InstructionPattern): BitPat = {
    val bjuOp = op.name match {
      case "jal" | "jalr" => BJUOpType.JUMP
      case "beq"          => BJUOpType.EQ
      case "bne"          => BJUOpType.NE
      case "blt"          => BJUOpType.LT
      case "bge"          => BJUOpType.GE
      case "bltu"         => BJUOpType.LTU
      case "bgeu"         => BJUOpType.GEU
      case _              => return BitPat.dontCare(BJUOpType.getWidth)
    }

    BitPat(bjuOp.litValue.U(BJUOpType.getWidth.W))
  }
}

object Data1Field extends DecodeField[InstructionPattern, UInt] {
  def name: String = "data1Type"
  def chiselType = UInt(Data1Type.getWidth.W)

  def genTable(op: InstructionPattern): BitPat = {
    val data1Type = op.name match {
      case "auipc" | "jal" => Data1Type.PC
      case _               => Data1Type.RS1
    }

    BitPat(data1Type.litValue.U(Data1Type.getWidth.W))
  }

}

object Data2Field extends DecodeField[InstructionPattern, UInt] {
  def name: String = "data2Type"
  def chiselType = UInt(Data2Type.getWidth.W)
  def genTable(op: InstructionPattern): BitPat = {
    val data2Type = if (op.hasArg("rs2")) Data2Type.RS2 else Data2Type.IMM

    BitPat(data2Type.litValue.U(Data2Type.getWidth.W))
  }

}

object RegWriteEnableField extends BoolDecodeField[InstructionPattern] {
  def name: String = "regWriteEnable"
  def genTable(op: InstructionPattern): BitPat = BitPat(op.hasArg("rd").B)
}

object NextPCDataTypeField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "nextPCDataType"
  def chiselType = UInt(NextPCDataType.getWidth.W)
  def genTable(op: InstructionPattern): BitPat = {
    val nextPCType = op.name match {
      case "jal" | "jalr" | "beq" | "bne" | "blt" | "bge" | "bltu" | "bgeu" =>
        NextPCDataType.BRANCHJUMP
      case "ecall" | "mret" => NextPCDataType.CSRDATA
      case _                => NextPCDataType.NORMAL
    }

    BitPat(nextPCType.litValue.U(NextPCDataType.getWidth.W))
  }
}

object MemLenField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "memoryLenth"
  def chiselType = UInt(MemSize.getWidth.W)
  def genTable(op: InstructionPattern): BitPat = {
    val memSize = op.name match {
      case "lh" | "lhu" | "sh" => MemSize.H
      case "lw" | "sw"         => MemSize.W
      case "lb" | "lbu" | "sb" => MemSize.B
      case _                   => return BitPat.dontCare(MemSize.getWidth)
    }

    BitPat(memSize.litValue.U(MemSize.getWidth.W))
  }
}

object UnsignField extends BoolDecodeField[InstructionPattern] {
  def name: String = "unsign"
  def genTable(op: InstructionPattern): BitPat = {
    op.name match {
      case "lbu" | "lhu"      => BitPat(true.B)
      case "lb" | "lh" | "lw" => BitPat(false.B)
      case _                  => BitPat.dontCare(1)
    }
  }
}

object BreakField extends BoolDecodeField[InstructionPattern] {
  def name: String = "break"

  // Only EBREAK has a break signal
  def genTable(op: InstructionPattern): BitPat = {
    BitPat((op.name == "ebreak").B)
  }
}

object DecodeSupportField extends DecodeField[InstructionPattern, Bool] {
  def name: String = "decodeSupport"
  def chiselType = Bool()
  def genTable(op: InstructionPattern): BitPat = BitPat.Y(1)
  override def default: BitPat = BitPat.N(1)
}

object CSROPTypeField extends DecodeField[InstructionPattern, UInt] {
  def name: String = "csrOperation"
  def chiselType = UInt(CSROPType.getWidth.W)
  def genTable(op: InstructionPattern): BitPat = {
    val csrOp = op.name match {
      case "ecall" => CSROPType.CALL
      case "mret"  => CSROPType.RET
      case "csrrw" => CSROPType.RW
      case "csrrs" => CSROPType.RS
      case _       => return BitPat.dontCare(CSROPType.getWidth)
    }

    BitPat(csrOp.litValue.U(CSROPType.getWidth.W))
  }
}

object RVDecoderDBSource {
  private val riscvOpcodesPath = os.pwd / "rvdecoderdb" / "riscv-opcodes"

  private val rv32ShiftImmediate = Set("slli", "srli", "srai")
  private val supportedM =
    Set("mul", "mulh", "mulhsu", "mulhu", "div", "divu", "rem", "remu")
  private val supportedCSR = Set("csrrw", "csrrs")
  private val supportedSystem = Set("mret")

  def currentSupported: Seq[InstructionPattern] = {
    require(
      os.exists(riscvOpcodesPath / "extensions"),
      s"Missing riscv-opcodes extensions directory: ${riscvOpcodesPath}"
    )
    require(
      os.exists(riscvOpcodesPath / "arg_lut.csv"),
      s"Missing riscv-opcodes arg_lut.csv: ${riscvOpcodesPath}"
    )

    val instructionDB = org.chipsalliance.rvdecoderdb
      .instructions(riscvOpcodesPath)
      .toSeq

    instructionDB
      .filter { instruction =>
        instruction.instructionSet.name match {
          case "rv_i" =>
            instruction.pseudoFrom.isEmpty && instruction.name != "fence"
          case "rv_m"     => supportedM.contains(instruction.name)
          case "rv32_i"   => rv32ShiftImmediate.contains(instruction.name)
          case "rv_zicsr" =>
            instruction.pseudoFrom.isEmpty && supportedCSR.contains(
              instruction.name
            )
          case "rv_system" => supportedSystem.contains(instruction.name)
          case _           => false
        }
      }
      .sortBy(instruction =>
        (instruction.instructionSet.name, instruction.name)
      )
      .map(InstructionPattern(_))
  }
}

object IDUTable {

  val possiblePatterns = RVDecoderDBSource.currentSupported

  val allFields = Seq(
    DecodeSupportField,
    FuncTypeField,
    FuncOpTypeField,
    ImmField,
    Data1Field,
    Data2Field,
    RegWriteEnableField,
    NextPCDataTypeField,
    MemLenField,
    UnsignField,
    BreakField,
    CSROPTypeField
  )

  val decodeTable = new DecodeTable(possiblePatterns, allFields)
}
