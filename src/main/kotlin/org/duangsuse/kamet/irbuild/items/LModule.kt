package org.duangsuse.kamet.irbuild.items

import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.irbuild.withErrorHandling

abstract class LRefContainer<T: Pointer>(val llvm: T)
interface Disposable { fun dispose() }

typealias LType = LLVMTypeRef
typealias LValue = LLVMValueRef
typealias LGenericValue = LLVMGenericValueRef
typealias LBasicBlock = LLVMBasicBlockRef

class LModule(name: String): LRefContainer<LLVMModuleRef>(LLVM.LLVMModuleCreateWithName(name)), Disposable {
  private val engine by lazy { LLVMExecutionEngineRef() }
  override fun dispose() { LLVM.LLVMDisposeExecutionEngine(engine) }

  fun addFunction(name: String, type: LType, call_conv: Int = LLVM.LLVMCCallConv): LFunction
    = LLVM.LLVMAddFunction(llvm, name, type).also { LLVM.LLVMSetFunctionCallConv(it, call_conv) }.let(::LFunction)
  fun runFunction(fn: LFunction, vararg args: LGenericValue): LGenericValue
    = LLVM.LLVMRunFunction(engine, fn.llvm, args.size, args[0])
  fun createJITCompiler(optLevel: Int) {
    withErrorHandling("create JIT") { LLVM.LLVMCreateJITCompilerForModule(engine, llvm, optLevel, it) }
  }

  fun runVerify(mode: Int = LLVM.LLVMAbortProcessAction) = withErrorHandling { LLVM.LLVMVerifyModule(llvm, mode, it) }
  fun debugDump() = LLVM.LLVMDumpModule(llvm)
  companion object {
    fun setupLLVMNative() {
      LLVM.LLVMLinkInMCJIT()
      LLVM.LLVMInitializeNativeAsmPrinter()
      LLVM.LLVMInitializeNativeAsmParser()
      LLVM.LLVMInitializeNativeDisassembler()
      LLVM.LLVMInitializeNativeTarget()
    }
  }
}