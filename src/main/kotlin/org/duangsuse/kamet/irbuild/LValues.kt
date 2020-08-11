package org.duangsuse.kamet.irbuild

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.items.LGenericValue
import org.duangsuse.kamet.irbuild.items.LType
import org.duangsuse.kamet.irbuild.items.LValue

object LValues {
  fun constInt(type: LType, value: Long, is_ext: Boolean = false): LValue
    = LLVMConstInt(type, value, is_ext.toInt())
  fun genericInt(type: LType, value: Long, is_ext: Boolean = false)
    = LLVMCreateGenericValueOfInt(type, value, is_ext.toInt())!!
  fun genericReal(type: LType, value: Double)
    = LLVMCreateGenericValueOfFloat(type, value)!!
  fun genericPointer(value: Pointer) = LLVMCreateGenericValueOfPointer(value)!!
  fun genericPointer(value: String) = genericPointer(BytePointer(value))

  val bTrue = genericInt(LTypes.i1, 1)
  val bFalse = genericInt(LTypes.i1, 0)
}

val LGenericValue.boolean get() = LLVMGenericValueToInt(this, 0) != 0L
val LGenericValue.long get() = LLVMGenericValueToInt(this, 1)
val LGenericValue.int get() = long.toInt()

val LGenericValue.double get() = LLVMGenericValueToFloat(LTypes.f64, this)
val LGenericValue.float get() = double.toFloat()

val LGenericValue.pointer get() = LLVMGenericValueToPointer(this)!!
val LGenericValue.string get() = BytePointer(pointer).string!!
