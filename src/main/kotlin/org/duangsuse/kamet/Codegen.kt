package org.duangsuse.kamet

import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.irbuild.items.LFunction
import org.duangsuse.kamet.irbuild.items.LModule
import org.duangsuse.kamet.irbuild.items.LPassManager
import java.lang.Error

class CodegenContext(val module: LModule) {
  val ir = IRBuilder()
  var func: LFunction? = null

  private class CascadeNameMap<V>: CascadeMap<String, V>()
  private val types = CascadeNameMap<Type>().apply { Types.putDefaultTypes(this) }
  private val values = CascadeNameMap<Value>()
  private val functions = CascadeNameMap<MutableList<Types.Function>>()

  fun lookupType(name: String) = types[name] ?: error("Unknown type ${name.escape()}")
  fun lookupFunctions(name: String) = functions[name] ?: emptyList<Types.Function>()

  fun declareType(type: Type) {
    if (types.containsKey(type.name)) error("Redeclare of type ${type.name}")
    types[type.name] = type
  }
  fun declare(name: String, value: Value) { values[name] = value }

  fun declareVariable(name: String, value: Value, isConst: Boolean = false): ValueRef {
    if (value.type.canImplicitlyCastTo(Types.Unit)) return Values.UnitValueRef(isConst)
    val function = LLVM.LLVMGetBasicBlockParent(ir.pos)
    val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
    val ir1 = IRBuilder()
    LLVM.LLVMPositionBuilder(ir1.llvm, entryBlock, LLVM.LLVMGetFirstInstruction(entryBlock))
    val address = LLVM.LLVMBuildAlloca(ir1.llvm, value.type.llvm, name)
    val allocated = ValueRef(address, value.type, isConst)
    LLVM.LLVMSetValueName2(address, name, name.length.toLong())
    ir1.dispose()
    declare(name, allocated)
    if (LLVM.LLVMIsUndef(value.llvm) == 0) allocated.setIn(ir, value)
    return allocated
  }

  internal fun declareFunction(prototype: PrototypeNode, value: Value) {
    val parameterTypes = (value.type as Types.Function).parameterTypes
    findDuplicate@ for (function in lookupFunctions(prototype.name)) {
      val type = function.type as Types.Function
      if (parameterTypes.size != type.parameterTypes.size) continue
      for (i in parameterTypes.indices)
        if (parameterTypes[i] != type.parameterTypes[i]) continue@findDuplicate
      error("Function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToCommaString()})")
    }
    declare(prototype.functionName, value)
    functions.getOrPut(prototype.name, ::mutableListOf) += value
  }

  fun runVerify() = try { module.runVerify(LLVM.LLVMReturnStatusAction); null }
    catch (e: Error) { e.message }

  object TypeTranslator: Types.Visitor<Type>

  companion object {
    fun topLevel(name: String) = CodegenContext(LModule(name))
    val passManager = LPassManager.CommonPasses.run {
      LPassManager().add(constProp, instCombine, reassociate)
        .add(gvn, cfgSimplify)
    }
  }
}
