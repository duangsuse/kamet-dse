package org.duangsuse.kamet

import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.ast.PrototypeNode
import org.duangsuse.kamet.irbuild.Consumer
import org.duangsuse.kamet.irbuild.IRBuilder
import org.duangsuse.kamet.irbuild.items.LModule
import org.duangsuse.kamet.irbuild.items.LPassManager
import java.lang.Error

private typealias Name = String
private typealias CascadeNameMap<V> = CascadeMap<Name, V>

private typealias TFunction = Types.Function

/**
 * General context([subContext]) for [module] codegen, with:
 * - types/values (cascading) and function overloads map
 * - [ir] for [IRBuilder]; [errors]
 * - [lookupType], [lookupFunctions], [getFunction]
 * - [declareType], [declareFunction] error when duplicate, [declareLocal] in current function
 * - [runVerify], [Companion.passManager] and [TypeResolve]
 */
class CodegenContext private constructor(
    val module: LModule,
    private val types: CascadeNameMap<Type>, private val values: CascadeNameMap<Value>,
    private val functions: MutableMap<Name, MutableList<Value>>) {
  constructor(module: LModule): this(module, CascadeNameMap(), CascadeNameMap(), mutableMapOf())
  val ir = IRBuilder()
  val errors: MutableList<String> = mutableListOf()
  fun error(message: String) { errors.add(message.capitalize()) }

  init { Types.putDefaultTypes(types) }
  fun subContext(): CodegenContext = CodegenContext(module, types.subMap(), values.subMap(), functions)

  fun lookupType(name: Name) = types[name] ?: Types.Unresolved(name).also { error("unknown type ${name.escape()}") }
  fun lookupFunctions(name: Name) = functions[name]?.filterIsInstance<TFunction>() ?: emptyList()
  private val functionUnresolved = Types.Function(Types.Nothing, emptyList())

  fun declareType(type: Type) {
    if (types.containsKey(type.name)) error("redeclare of type ${type.name}")
    types[type.name] = type
  }
  fun declare(name: Name, value: Value) { values[name] = value }

  fun declareFunction(prototype: PrototypeNode, value: Value) {
    val parameterTypes = (value.type as TFunction).parameterTypes
    if (findFunction(parameterTypes, lookupFunctions(prototype.name)) {}  != null)
      error("function ${prototype.name} redeclared with same parameter types: (${parameterTypes.joinToCommaString()})")
    declare(prototype.functionName, value)
    functions.getOrPut(prototype.name, ::mutableListOf).add(value)
  }
  fun declareLocal(name: String, value: Value, isConst: Boolean = false): ValueRef {
    val allocated = value.wrap { ir.allocateLocal(value.type.llvm, name) }.toRef(isConst)
    declare(name, allocated)
    if (LLVM.LLVMIsUndef(value.llvm) == 0) allocated.setIn(ir, value)
    return allocated
  }

  fun getFunction(signature: List<Type>, name: Name): TFunction {
    val functions = lookupFunctions(name).takeUnless { it.isEmpty() }
    if (functions == null) { error("no function named \"$name\""); return functionUnresolved }
    val found = findFunction(signature, functions) { error("ambiguous call to function \"$name\": ${it.first} and ${it.second} are both applicable") }
    return found ?: functionUnresolved.also {
      error("no matching function for call to \"$name\" with argument types: (${signature.joinToString { it.name }})")
    }
  }
  fun findFunction(signature: List<Type>, overloads: List<TFunction>, on_duplicate: Consumer<Twice<TFunction>>): TFunction? {
    var found: TFunction? = null
    findNext@ for (overload in overloads) {
      val params = overload.parameterTypes
      if (params.size != signature.size) continue@findNext
      for (i in params.indices) {
        if (!params[i].canImplicitlyCastTo(signature[i])) continue@findNext
      }
      if (found == null) found = overload
      else on_duplicate(overload to found)
    }
    return found
  }

  fun runVerify() = try { module.runVerify(LLVM.LLVMReturnStatusAction); null }
    catch (e: Error) { e.message }

  inner class TypeResolve: Types.Visitor<Type> {
    override fun see(t: Type) = t
    override fun see(t: Types.Unresolved) = lookupType(t.name)
    override fun see(t: TFunction) = t.run { TFunction(returnType.resolve(), parameterTypes.resolveAll()) }
    override fun see(t: Types.Array) = t.run { Types.Array(elementType.resolve(), size, isConst) }
    override fun see(t: Types.Struct) = t.run { Types.Struct(name, elements.map { this@TypeResolve(it) }, packed) }
    override fun see(t: Types.Prim.Integral) = t
    override fun see(t: Types.Prim.Real) = t
    override fun see(t: Types.Reference) = t.orig.resolve().reference(t.isConst)
    override fun see(t: Types.Pointer) = t.orig.resolve().pointer(t.isConst)
    private fun Type.resolve() = visitBy(this@TypeResolve)
    private fun Iterable<Type>.resolveAll() = map { it.resolve() }
    operator fun invoke(type: Type) = type.resolve()
    operator fun invoke(typed: Types.TypedName) = typed.wrap { it.resolve() }
  }
  private val typeResolve = TypeResolve()

  companion object {
    fun forName(name: String) = CodegenContext(LModule(name))
    val passManager = LPassManager.CommonPasses.run {
      LPassManager()
        .add(constProp, instCombine, reassociate)
        .add(gvn, cfgSimplify)
    }
  }
}
