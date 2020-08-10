package org.duangsuse.kamet.irbuild

import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.items.LType
import org.duangsuse.kamet.irbuild.items.LValue

object LTypes {
  val void = LLVMVoidType()
  fun i(n: Int) = LLVMIntType(n)
  val i1 = LLVMInt1Type()
  val i8 = LLVMInt8Type(); val i16 = LLVMInt16Type()
  val i32 = LLVMInt32Type(); val i64 = LLVMInt64Type()

  val f32 = LLVMFloatType(); val f64 = LLVMDoubleType()

  fun fnTyped(return_type: LType, vararg params_type: LType, is_vararg: Boolean = false)
    = LLVMFunctionType(return_type, params_type[0], params_type.size, is_vararg.toInt())
  fun unnamedStruct(vararg elements: LType, is_packed: Boolean) = LLVMStructType(elements[0], elements.size, is_packed.toInt())
  fun array(type: LType, size: Int) = LLVMArrayType(type, size)
}

fun LType.undefined(): LValue = LLVMGetUndef(this)
fun LType.nullPtr(): LValue = LLVMConstNull(this)
