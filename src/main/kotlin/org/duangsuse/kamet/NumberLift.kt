package org.duangsuse.kamet

import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.Types.Prim
import org.duangsuse.kamet.irbuild.items.LType
import org.duangsuse.kamet.irbuild.items.LValue

// just (size greater first, unsigned first) number lifting
// getLiftKind: used in cast
// getUnifiedLiftType and IRBuilder.lift: used in math ops

fun getLiftKind(type: Type, dest: Type): Int? = when (type) {
  is Prim.Integral ->
    when (dest) {
      is Prim.Integral ->
        if (bitSizeGT(type, dest)) LLVMTrunc
        else type.foldSign(LLVMSExt, LLVMZExt)
      is Prim.Real -> type.foldSign(LLVMSIToFP, LLVMUIToFP)
      is Prim.Boolean -> LLVMTrunc
      else -> null
    }
  is Prim.Boolean ->
    if (dest is Prim.Integral) dest.foldSign(LLVMSExt, LLVMZExt) else null
  is Prim.Real ->
    when (dest) {
      is Prim.Integral -> dest.foldSign(LLVMFPToSI, LLVMFPToUI)
      is Prim.Real -> if (bitSizeGT(type, dest)) LLVMFPTrunc else LLVMFPExt
      else -> null
    }
  else -> null
}

fun getUnifiedLiftType(a: Type, b: Type): Type =
  when {
    a !is Prim || b !is Prim -> TODO("unify data types")
    a == b &&
      a in arrayOf(Prim.Float, Prim.Double, Prim.Boolean) -> a
    else -> { // bit size first
      val lhsTypedI = a as Prim.Integral
      val lb = lhsTypedI.bitSize
      val rb = (b as Prim.Integral).bitSize
      when {
        lb > rb -> a
        lb == rb -> lhsTypedI.foldSign(unsigned = a, signed = b) // unsigned first
        lb < rb -> b
        else -> impossible()
      }
    }
  }

fun IRBuilder.lift(from: Value, dest: Type): Value { // numeric lifting casts
  val type = from.type
  if (type == dest) return from
  if (dest !is Prim) TODO("lifting cast dest !is Prim")

  fun fail(): Nothing = throw IllegalCastException(type, dest)
  fun castOp(op: (LLVMBuilderRef, LValue, LType, String) -> LValue, name: String) = op(llvm, from.llvm, dest.llvm, name)
  val coercion = when (dest) {
    is Prim.Integral -> { // destination type <- (from type)
      when (type) {
        is Prim.Integral ->
          if (dest.signed) castOp(::LLVMBuildSExt, "sext")
          else castOp(::LLVMBuildZExt, "zext")
        is Prim.Real ->
          if (dest.signed) castOp(::LLVMBuildFPToSI, "f2i")
          else castOp(::LLVMBuildFPToUI, "i2u")
        else -> fail()
      }
    }
    is Prim.Real -> {
      when (type) {
        is Prim.Integral ->
          if (type.signed) castOp(::LLVMBuildSIToFP, "i2f")
          else castOp(::LLVMBuildUIToFP, "u2f")
        is Prim.Real ->
          castOp(::LLVMBuildFPExt, "fext")
        else -> fail()
      }
    }
  }
  return Value(coercion, dest)
}

private fun bitSizeGT(a: Prim, b: Prim) = a.bitSize > b.bitSize
