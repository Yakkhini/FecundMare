package taohe

import chisel3._
import chisel3.util.{switch, is}
import chisel3.util.Cat

import taohe.util.ICacheBundle

object ICacheState extends ChiselEnum {
  val sIdle, sSend = Value
}

class ICache extends Module {

  val io = IO(new ICacheBundle)
  val state = RegInit(ICacheState.sIdle)

  val cache = RegInit(VecInit(Seq.fill(16)(0.U(59.W))))

  val pcBuffer = RegInit(0.U(32.W))
  pcBuffer := io.fromIFU.bits.pc

  val transferPC =
    Mux(state === ICacheState.sIdle, io.fromIFU.bits.pc, pcBuffer)

  val index = transferPC(5, 2)
  val readCacheLine = cache(index)

  val readData = RegInit(0.U(32.W))
  val readValid = readCacheLine(58)
  val readTag = readCacheLine(57, 32)
  val readHit = RegInit(false.B)

  readData := readCacheLine(31, 0)
  readHit := readValid && (readTag === transferPC(31, 6))

  io.toIFU.bits.readData := readData
  io.toIFU.bits.hit := readHit

  io.fromIFU.ready := true.B
  io.toIFU.valid := state === ICacheState.sSend

  val writeTag = io.fromIFU.bits.pc(31, 6)
  val writeData = io.fromIFU.bits.writeData
  val writeCacheLine = Cat(1.U(1.W), writeTag, writeData)

  cache(index) := Mux(
    io.fromIFU.bits.writeEnable,
    writeCacheLine,
    readCacheLine
  )

  switch(state) {
    is(ICacheState.sIdle) {
      when(io.fromIFU.fire) {
        state := Mux(
          io.fromIFU.bits.writeEnable,
          ICacheState.sIdle,
          ICacheState.sSend
        )
      }
    }
    is(ICacheState.sSend) {
      state := ICacheState.sIdle
    }
  }

}
