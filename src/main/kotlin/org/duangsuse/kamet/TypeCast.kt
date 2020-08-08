package org.duangsuse.kamet

import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.irbuild.IRBuilder

typealias TPairPredicate = (Type, Type) -> Boolean

/**
 * Implicit cast (A as B) success rules:
 * - (A = B)
 * - (A=Nothing* and B:*)
 * - (A:& and canImplicitlyCast(A, B))
 *
 * Explicit cast is an extension to implicit one
 */
internal object CastManager {
  private val case1: TPairPredicate = { a, b -> a == b }
  private val case2: TPairPredicate = { a, b -> a is Types.Pointer && (a.orig == Types.Nothing) && b.isPointer }
  private val case3: TPairPredicate = { a, b -> a is Types.Reference && canImplicitlyCast(a.orig, b) }
  private val allCases = arrayOf(case1, case2, case3)

  fun canImplicitlyCast(from: Type, to: Type) = allCases.any { it(from, to) } // recursive case3

  private fun implicitCastOrNull(ir: IRBuilder, from: Value, to: Type): Value? = when {
    case1(from.type, to) -> from
    case2(from.type, to) -> Value(LLVM.LLVMBuildBitCast(ir.llvm, from.llvm, to.llvm, "pointer_cast"), type = to)
    from.type is Types.Reference -> implicitCastOrNull(ir, from.derefIn(ir), to)
    else -> null
  }
  fun implicitCast(ir: IRBuilder, from: Value, to: Type): Value = implicitCastOrNull(ir, from, to) ?: fail(from, to)

  @Suppress("NOTHING_TO_INLINE")
  inline fun fail(from: Value, to: Type): Nothing = error("attempt to cast a ${from.type} to $to")

  fun explicitCastOrNull(ir: IRBuilder, from: Value, dest: Type): Value? {
    implicitCastOrNull(ir, from, dest)?.let { return it }
    return when {
      from.type.isPointer && dest.isPointer -> Value(LLVM.LLVMBuildBitCast(ir.llvm, from.llvm, dest.llvm, "pointer_cast"), dest)
      from.type is Types.Prim && dest is Types.Prim -> Value(LLVM.LLVMBuildCast(ir, when (val type = from.type) {
        is Types.Prim.Integral ->
          when (dest) {
            is Types.Prim.Integral -> when {
              type.bitSize > dest.bitSize -> LLVM.LLVMTrunc
              type.bitSize <= dest.bitSize -> type.foldSign(LLVM.LLVMSExt, LLVM.LLVMZExt)
              else -> impossible() // TODO ^ should this being removed? (added for readability)
            }
            is Types.Prim.Real -> type.foldSign(LLVM.LLVMSIToFP, LLVM.LLVMUIToFP)
            is Types.Prim.Boolean -> LLVM.LLVMTrunc
          }
        is Types.Prim.Real ->
          when (dest) {
            is Types.Prim.Integral -> dest.foldSign(LLVM.LLVMFPToSI, LLVM.LLVMFPToUI)
            is Types.Prim.Real -> when {
              type.bitSize > dest.bitSize -> LLVM.LLVMFPTrunc
              type.bitSize <= dest.bitSize -> LLVM.LLVMFPExt
              else -> impossible()
            }
            else -> fail(from, dest)
          }
        is Types.Prim.Boolean ->
          when (dest) {
            is Types.Prim.Integral -> dest.foldSign(LLVM.LLVMSExt, LLVM.LLVMZExt)
            is Types.Prim.Real -> LLVM.LLVMUIToFP
            else -> impossible()
          }
        else -> TODO()
      }, from.llvm, dest.llvm, "primitive_cast"
      ), type = dest)
      from.type.isReference -> explicitCastOrNull(ir, from.derefIn(ir), dest) // TODO < try order rearranged, no rechecks after recursive call
      else -> null
    }
  }

  fun explicitCast(ir: IRBuilder, from: Value, to: Type) = explicitCastOrNull(ir, from, to) ?: fail(from, to)
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Value.implicitCast(ir: IRBuilder, to: Type) = CastManager.implicitCast(ir, this, to)

@Suppress("NOTHING_TO_INLINE")
internal inline fun Type.canImplicitlyCastTo(to: Type) = CastManager.canImplicitlyCast(this, to)
