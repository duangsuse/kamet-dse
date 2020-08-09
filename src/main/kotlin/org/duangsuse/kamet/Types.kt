package org.duangsuse.kamet

import org.duangsuse.kamet.irbuild.*
import org.duangsuse.kamet.irbuild.items.LType

open class Type(val name: String, val llvm: LType) {
  open fun <R> visitBy(vis: Types.Visitor<R>): R = vis.see(this)
  open fun undefIn(ir: IRBuilder) = Value(llvm.undef(), this)
  fun nullPtr() = pointer().let { Value(it.llvm.nullPtr(), it) }
}

/**
 * Types and their [Visitor], name reference [Unresolved], and [TypedName], [TypeModifier], with:
 * - special type [Nothing] and [Unit]
 * - [Function], [Struct], [Array]
 * - [Reference] and [Pointer] (modifiers)
 * - Primitive [Prim.Integral], [Prim.Real], and [putDefaultTypes]
 */
object Types {
  interface Visitor<out R> {
    fun see(t: Type): R
    fun see(t: Unresolved): R
    fun see(t: Function): R
    fun see(t: Struct): R
    fun see(t: Array): R
    fun see(t: Prim.Integral): R
    fun see(t: Prim.Real): R
    fun see(t: Reference): R
    fun see(t: Pointer): R
  }
  data class TypedName(val name: String, val type: Type) {
    fun wrap(op: Pipe<Type>) = TypedName(name, op(type))
  }

  class Unresolved(name: String): Type(name, LTypes.void) {
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }
  interface TypeModifier { // reference, pointer
    val orig: Type
    fun checkOrigNotReference(modifier_name: String) = require(orig !is Reference) { "creating a $modifier_name of a reference" }
  }
  object Nothing: Type("Nothing", LTypes.void)
  object Unit: Type("Unit", LTypes.void)

  data class Function(val returnType: Type, val parameterTypes: List<Type>):
    Type("$returnType(${parameterTypes.joinToCommaString()})",
      LTypes.fnTyped(returnType.llvm, *parameterTypes.mapToArray(Type::llvm))) {
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }
  class Struct(name: String, val elements: List<TypedName>, val packed: Boolean):
    Type(name, LTypes.unnamedStruct(*elements.mapToArray { it.type.llvm }, is_packed = packed)) {
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
    fun indexOf(name: String) = elements.indexOfFirst { it.name == name }.also {
      if (it == -1) throw NoSuchElementException("struct ${this.name} has no member named $name")
    }
    operator fun get(name: String) = elements[indexOf(name)]
  }
  data class Array(val elementType: Type, val size: Int, val isConst: Boolean,
    private val rawType: LType = LTypes.array(elementType.llvm, size)
  ): Type(
    "[${"const ".showIf(isConst)}$elementType, $size]",
    elementType.pointer(isConst).llvm
  ) {
    override fun undefIn(ir: IRBuilder): Value =
      Value(ir.bitCast(ir.allocateLocal(rawType, "array"), llvm, "ary2ptr"), this)
  }

  sealed class Prim(name: String, type: LType, val bitSize: kotlin.Int): Type(name, type) {
    open class Integral(name: String, bitSize: kotlin.Int, val signed: kotlin.Boolean = true): Prim(name, LTypes.i(bitSize), bitSize) {
      override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
      fun <T> foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned
    }
    open class UIntegral(name: String, bitSize: kotlin.Int): Integral(name, bitSize, signed = false)
    open class Real(name: String, llvm: LType, bitSize: kotlin.Int): Prim(name, llvm, bitSize) {
      override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
    }

    object Boolean: Integral("Boolean", 1)
    object Byte: Integral("Byte", 8)

    object Short: Integral("Short", 16)
    object Char: Integral("Char", 16)
    object Int: Integral("Int", 32)
    object Long: Integral("Long", 64)

    object UByte: UIntegral("UByte", 8)
    object UShort: UIntegral("UShort", 16)
    object UInt: UIntegral("UInt", 32)
    object ULong: UIntegral("ULong", 64)

    object Float: Real("Float", LTypes.f32, 32)
    object Double: Real("Double", LTypes.f64, 64)
  }

  private fun showModifier(is_const: Boolean, orig: Type) = "${"const ".showIf(is_const)}($orig)"
  data class Reference(override val orig: Type, val isConst: Boolean): TypeModifier,
    Type("&${showModifier(isConst, orig)}", orig.llvm.pointer()) {
    init { checkOrigNotReference("reference") }
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }
  data class Pointer(override val orig: Type, val isConst: Boolean): TypeModifier,
    Type("*${showModifier(isConst, orig)}", orig.llvm.pointer()) {
    init { checkOrigNotReference("pointer") }
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }

  private val defaultTypes = arrayOf(
    Nothing,
    Unit,
    Prim.Boolean,
    Prim.Byte,
    Prim.Short,
    Prim.Char,
    Prim.Int,
    Prim.Long,
    Prim.Float,
    Prim.Double,
    Prim.UByte,
    Prim.UShort,
    Prim.UInt,
    Prim.ULong)
  fun putDefaultTypes(map: MutableMap<String, Type>) {
    for (type in defaultTypes) map[type.name] = type
  }
}

internal inline val Type.isReference get() = this is Types.Reference
internal inline val Type.isPointer get() = this is Types.Pointer

fun Type.reference(isConst: Boolean = false) = Types.Reference(this, isConst)
fun Type.pointer(isConst: Boolean = false) = Types.Pointer(this, isConst)
