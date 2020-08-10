package org.duangsuse.kamet

import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.TypeCast.canImplicitlyCast
import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.irbuild.items.LValue

typealias PairPredicate<T> = (T, T) -> Boolean
typealias TPairPredicate = PairPredicate<Type>
/**
 * Implicit cast `(A as B)` success rules:
 * - A = B
 * - A = Nothing
 * - (A = Nothing*) and (B is [Types.Pointer])
 * - (A is [Types.Reference]) and `canImplicitlyCast(A.origType, B)`
 * - A, B are both pointer; a.origType is Nothing or b
 *
 * Explicit cast is an extension to implicit cast
 */
object TypeCast {
  private val case1: TPairPredicate = { a, b -> a == b }
  private val case2: TPairPredicate = { a, _ -> a == Types.Nothing }
  private val case3: TPairPredicate = { a, b -> a is Types.Reference && canImplicitlyCast(a.orig, b) }
  private val case4 = twoPtrPredicate { a, _ -> a.orig == Types.Nothing }
  private val case5 = twoPtrPredicate { a, b -> a.orig == b.orig }
  private inline fun twoPtrPredicate(crossinline origin: PairPredicate<Types.Pointer>): TPairPredicate
    = { a, b -> a is Types.Pointer && b is Types.Pointer && origin(a, b) }
  private val allCases = arrayOf(case1, case2, case3, case4, case5)

  fun canImplicitlyCast(type: Type, dest: Type) = allCases.any { it(type, dest) }
  private fun fail(from: Value, dest: Type): Nothing = error("attempt to cast a ${from.type} into $dest")

  private fun IRBuilder.implicitCastOrNull(from: Value, dest: Type): Value? {
    val type = from.type
    fun match(p: TPairPredicate) = p(type, dest)
    return when {
      match(case1) -> from
      match(case2) -> from.also { unreachable() }
      /*case3*/(type is Types.Reference) -> implicitCastOrNull(from.derefIn(this), dest)
      match(case4) || match(case5) -> bitCast(from.llvm, dest.llvm, "pointer_cast").typed(dest)
      else -> null
    }
  }
  private fun IRBuilder.explicitCastOrNull(from: Value, dest: Type): Value? {
    val type = from.type
    return implicitCastOrNull(from, dest) ?:
    when {
      type.isReference -> explicitCastOrNull(from.derefIn(this), dest)
      type.isPointer && dest.isPointer -> bitCast(from.llvm, dest.llvm, "pointer_cast").typed(dest)
      type.isPointer && dest is Types.Prim.Integral -> LLVM.LLVMBuildPtrToInt(llvm, from.llvm, dest.llvm, "i2p").typed(dest)
      type.isPrim && dest.isPrim -> cast(getLiftKind(type, dest) ?: fail(from, dest),
        from.llvm, dest.llvm, "primitive_cast").typed(dest)
      else -> null
    }
  }

  fun IRBuilder.implicitCast(from: Value, dest: Type): Value = implicitCastOrNull(from, dest) ?: fail(from, dest)
  fun IRBuilder.explicitCast(from: Value, dest: Type) = explicitCastOrNull(from, dest) ?: fail(from, dest)
}

fun Type.canImplicitlyCastTo(dest: Type) = canImplicitlyCast(this, dest)
fun LValue.typed(type: Type) = Value(this, type)
