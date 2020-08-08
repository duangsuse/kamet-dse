package org.duangsuse.kamet.irbuild.items

import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM
import org.duangsuse.kamet.irbuild.Consumer

class LPassManager: LRefContainer<LLVMPassManagerRef>(LLVM.LLVMCreatePassManager()), Disposable {
  /** Return `true` for modified */
  fun runOn(mod: LModule): Boolean = LLVM.LLVMRunPassManager(llvm, mod.llvm) == 1
  fun add(vararg addPass: Consumer<LLVMPassManagerRef>) = addPass.forEach { it(llvm) }

  override fun dispose() { LLVM.LLVMDisposePassManager(llvm) }

  object CommonPasses {
    val all: List<Consumer<LLVMPassManagerRef>> = listOf(
      LLVM::LLVMAddConstantPropagationPass, LLVM::LLVMAddInstructionCombiningPass,
      LLVM::LLVMAddReassociatePass, LLVM::LLVMAddGVNPass,
      LLVM::LLVMAddCFGSimplificationPass, LLVM::LLVMAddPromoteMemoryToRegisterPass,
      LLVM::LLVMAddTailCallEliminationPass, LLVM::LLVMAddDCEPass, LLVM::LLVMAddEarlyCSEPass, LLVM::LLVMAddLICMPass)
    private val next = all.iterator()::next
    val constProp = next(); val instCombine = next()
    val reassociate = next(); val gvn = next()
    val cfgSimplify = next();val mem2reg = next()
    val tailCallElim = next(); val deadCodeElim = next()
    val earlyCommonSubexprElim = next(); val licm = next()
  }
}