package org.duangsuse.kamet.irbuild.items

import org.bytedeco.llvm.global.LLVM
import org.bytedeco.llvm.global.LLVM.LLVMAppendBasicBlock

class LFunction internal constructor(llvm: LValue): LRefContainer<LValue>(llvm) {
  operator fun get(index: Int): LValue = LLVM.LLVMGetParam(llvm, index)
  fun basicBlock(name: String): LBasicBlock = LLVMAppendBasicBlock(llvm, name)
}