package org.duangsuse.kamet

import org.duangsuse.kamet.TypeCast.canImplicitlyCast
import org.duangsuse.kamet.irbuild.IRBuilder

typealias TPairPredicate = (Type, Type) -> Boolean
/**
 * Implicit cast `(A as B)` success rules:
 * - A = B
 * - A = Nothing
 * - (A = Nothing*) and (B is [Types.Pointer])
 * - (A is [Types.Reference]) and `canImplicitlyCast(A.origType, B)`
 *
 * Explicit cast is an extension to implicit cast
 */
object TypeCast {
  private val case1: TPairPredicate = { a, b -> a == b }
  private val case2: TPairPredicate = { a, _ -> a == Types.Nothing }
  private val case3: TPairPredicate = { a, b -> a is Types.Pointer && (a.orig == Types.Nothing) && b.isPointer }
  private val case4: TPairPredicate = { a, b -> a is Types.Reference && canImplicitlyCast(a.orig, b) }
  private val allCases = arrayOf(case1, case2, case3, case4)

  fun canImplicitlyCast(type: Type, dest: Type) = allCases.any { it(type, dest) }
  private fun fail(from: Value, dest: Type): Nothing = error("attempt to cast a ${from.type} into $dest")

  private fun IRBuilder.implicitCastOrNull(from: Value, dest: Type): Value? {
    val type = from.type
    return when {
      case1(type, dest) -> from
      case2(type, dest) -> from.also { unreachable() }
      case3(type, dest) -> Value(bitCast(from.llvm, dest.llvm, "pointer_cast"), type = dest)
      type is Types.Reference -> implicitCastOrNull(from.derefIn(this), dest)
      else -> null
    }
  }
  private fun IRBuilder.explicitCastOrNull(from: Value, dest: Type): Value? {
    val type = from.type
    return implicitCastOrNull(from, dest) ?:
    when {
      type.isReference -> explicitCastOrNull(from.derefIn(this), dest)
      type.isPointer && dest.isPointer -> Value(bitCast(from.llvm, dest.llvm, "pointer_cast"), dest)
      type is Types.Prim && dest is Types.Prim -> Value(cast(getLiftKind(type, dest) ?: fail(from, dest),
        from.llvm, dest.llvm, "primitive_cast"), type = dest)
      else -> null
    }
  }

  fun IRBuilder.implicitCast(from: Value, dest: Type): Value = implicitCastOrNull(from, dest) ?: fail(from, dest)
  fun IRBuilder.explicitCast(from: Value, dest: Type) = explicitCastOrNull(from, dest) ?: fail(from, dest)
}

fun Type.canImplicitlyCastTo(dest: Type) = canImplicitlyCast(this, dest)
