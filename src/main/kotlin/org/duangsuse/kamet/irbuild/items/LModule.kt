package org.duangsuse.kamet.irbuild.items

import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.withErrorHandling

abstract class LRefContainer<T: Pointer>(val llvm: T)
interface Disposable { fun dispose() }
inline fun <T: Disposable, R> T.use(op: T.() -> R): R = op().also { dispose() }

typealias LType = LLVMTypeRef
typealias LValue = LLVMValueRef
typealias LGenericValue = LLVMGenericValueRef
typealias LBasicBlock = LLVMBasicBlockRef

class LModule(name: String): LRefContainer<LLVMModuleRef>(LLVMModuleCreateWithName(name)), Disposable {
  private val engine by lazy { LLVMExecutionEngineRef() }
  override fun dispose() { LLVMDisposeExecutionEngine(engine) }

  fun addFunction(name: String, type: LType, call_conv: Int = LLVMCCallConv): LFunction
    = LLVMAddFunction(llvm, name, type).also { LLVMSetFunctionCallConv(it, call_conv) }.let(::LFunction)
  fun runFunction(fn: LFunction, vararg args: LGenericValue): LGenericValue
    = LLVMRunFunction(engine, fn.llvm, args.size, args[0])
  fun createJITCompiler(optLevel: Int) {
    withErrorHandling("create JIT") { LLVMCreateJITCompilerForModule(engine, llvm, optLevel, it) }
  }

  fun runVerify(mode: Int = LLVMAbortProcessAction) = withErrorHandling { LLVMVerifyModule(llvm, mode, it) }
  fun debugDump() = LLVMDumpModule(llvm)
  companion object {
    fun setupLLVMNative() {
      LLVMLinkInMCJIT()
      LLVMInitializeNativeAsmPrinter()
      LLVMInitializeNativeAsmParser()
      LLVMInitializeNativeDisassembler()
      LLVMInitializeNativeTarget()
    }
  }
}
