package org.duangsuse.kamet

import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.irbuild.Pipe
import org.duangsuse.kamet.irbuild.items.LValue
import org.duangsuse.kamet.irbuild.undef

/**
 * Value with [Value.derefIn], and its Ref with assign([ValueRef.setIn]).
 * - [Unit] / [UnitValueRef]
 */
object Values {
  val Unit = Value(Types.Unit.llvm.undef(), Types.Unit)
  class UnitValueRef(isConst: Boolean): ValueRef(Types.Unit.nullPtr().llvm, Types.Unit, isConst) {
    override fun setIn(ir: IRBuilder, value: Value) {}
    override fun derefIn(ir: IRBuilder): Value = Unit
  }
}

open class Value(val llvm: LValue, val type: Type) {
  open fun derefIn(ir: IRBuilder) = this
  operator fun component1() = llvm
  operator fun component2() = type
  fun wrap(op: Pipe<LValue>) = Value(op(llvm), type)
}
fun Value.toRef(isConst: Boolean) = ValueRef(llvm, type, isConst)

open class ValueRef(allocated: LValue, type: Type, isConst: Boolean): Value(allocated, type.reference(isConst)) {
  open fun setIn(ir: IRBuilder, value: Value) {
    if (referType.isConst) error("attempt to alter a const reference")
    ir.store(value.llvm, this.llvm)
  }
  override fun derefIn(ir: IRBuilder): Value = Value(ir.load(this.llvm), referType.orig)
  private val referType get() = (type as Types.Reference)
}
