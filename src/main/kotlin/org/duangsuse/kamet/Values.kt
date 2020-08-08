package org.duangsuse.kamet

import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.irbuild.items.LValue
import org.duangsuse.kamet.irbuild.undef

/**
 * Value with deref, and its Ref with assign(setIn).
 * - Unit / UnitValueRef
 */
object Values {
  val Unit = Value(Types.Unit.llvm.undef(), Types.Unit)
  class UnitValueRef(isConst: Boolean): ValueRef(Types.Unit.nullPtr().llvm, Types.Unit, isConst) {
    override fun setIn(ir: IRBuilder, value: Value) {}
    override fun derefIn(ir: IRBuilder): Value = Unit
  }
}

open class Value(val llvm: LValue, val type: Types.Type) {
  open fun derefIn(ir: IRBuilder) = this
  operator fun component1() = llvm
  operator fun component2() = type
}

open class ValueRef(address: LLVMValueRef, val orig: Types.Type, val isConst: Boolean): Value(address, orig.reference(isConst)) {
  open fun setIn(ir: IRBuilder, value: Value) {
    if (isConst) error("Attempt to alter a const reference")
    ir.store(value.llvm, this.llvm)
  }
  override fun derefIn(ir: IRBuilder): Value = Value(ir.load(this.llvm), orig)
}
