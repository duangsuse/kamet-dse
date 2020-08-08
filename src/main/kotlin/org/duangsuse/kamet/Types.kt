package org.duangsuse.kamet

import org.duangsuse.kamet.irbuild.*
import org.duangsuse.kamet.irbuild.items.LType
import org.duangsuse.kamet.irbuild.showIf

open class Type(val name: String, val llvm: LType) {
  open fun <R> visitBy(vis: Types.Visitor<R>): R = vis.see(this)
  fun undef() = Value(llvm.undef(), this)
  fun nullPtr() = pointer().let { Value(it.llvm.nullPtr(), it) }
}

object Types {
  data class TypedName(val name: String, val type: Type)
  class Named(name: String): Type(name, LTypes.void) {
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }
  object Nothing: Type("Nothing", LTypes.void)
  object Unit: Type("Unit", LTypes.void)

  data class Function(val returnType: Type, val parameterTypes: List<Type>):
    Type("$returnType(${parameterTypes.joinToCommaString()})",
      LTypes.fnTyped(returnType.llvm, *parameterTypes.mapToArray { it.llvm })) {
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

  sealed class Prim(name: String, type: LType): Type(name, type) {
    open class Integral(name: String, val bitSize: kotlin.Int, val signed: kotlin.Boolean = true): Type(name, LTypes.i(bitSize)) {
      override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
    }
    open class UIntegral(name: String, bitSize: kotlin.Int): Integral(name, bitSize, signed = false)
    open class Real(name: String, llvm: LType, val bitSize: kotlin.Int): Type(name, llvm) {
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

  data class Reference(val orig: Type, val isConst: Boolean):
    Type("&${"const ".showIf(isConst)}($orig)", orig.llvm.pointer()) {
    init { require(orig !is Reference) { "creating a reference of a reference" } }
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }
  data class Pointer(val orig: Type, val isConst: Boolean):
    Type("*${"const ".showIf(isConst)}($orig)", orig.llvm.pointer()) {
    init { require(orig !is Reference) { "creating a pointer of a reference" } }
    override fun <R> visitBy(vis: Visitor<R>): R = vis.see(this)
  }

  interface Visitor<out R> {
    fun see(t: Type): R
    fun see(t: Prim.Integral): R
    fun see(t: Prim.Real): R
    fun see(t: Reference): R
    fun see(t: Pointer): R
    fun see(t: Function): R
    fun see(t: Struct): R
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

internal fun <T> Types.Prim.Integral.foldSign(signed: T, unsigned: T) = if (this.signed) signed else unsigned

fun Type.reference(isConst: Boolean = false) = Types.Reference(this, isConst)
fun Type.pointer(isConst: Boolean = false) = Types.Pointer(this, isConst)
