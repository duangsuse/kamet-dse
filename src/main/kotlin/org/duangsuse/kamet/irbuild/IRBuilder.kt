package org.duangsuse.kamet.irbuild

import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.items.*
import org.duangsuse.kamet.splitPairs

class IRBuilder: LRefContainer<LLVMBuilderRef>(LLVMCreateBuilder()), Disposable {
  var pos: LBasicBlock
    get() = LLVMGetInsertBlock(llvm)
    set(bb) { LLVMPositionBuilderAtEnd(llvm, bb) }
  override fun dispose() { LLVMDisposeBuilder(llvm) }
  inline fun <R> runInside(bb: LBasicBlock, crossinline op: IRBuilder.() -> R): R { pos = bb; return this.op() }
  inline fun <R> runInsideNext(bb: LBasicBlock, then: LBasicBlock, crossinline op: IRBuilder.() -> R): R
    = this.runInside(bb, op).also { br(then) }

  // "CBP" icmp, br/condBr/ret, phi
  fun icmp(op: Int, a: LValue, b: LValue, name: String = "cmp") = LLVMBuildICmp(llvm, op, a, b, name)
  fun br(then_bb: LBasicBlock) = LLVMBuildBr(llvm, then_bb)
  fun condBr(cond: LValue, then_bb: LBasicBlock, else_bb: LBasicBlock) = LLVMBuildCondBr(llvm, cond, then_bb, else_bb)
  fun ret(result: LValue) = LLVMBuildRet(llvm, result)
  fun phi(type: LType, vararg branches: Pair<LValue, LBasicBlock>, name: String = "res"): LValue {
    val node = LLVMBuildPhi(llvm, type, name)
    val (vs, bbs) = branches.asIterable().splitPairs { it }
    LLVMAddIncoming(node, PointerPointer(*vs.toTypedArray()), PointerPointer(*bbs.toTypedArray()), vs.size)
    return node
  }

  fun load(value: LValue, name: String = "load") = LLVMBuildLoad(llvm, value, name)
  fun store(value: LValue, dest: LValue) = LLVMBuildStore(llvm, value, dest)
  fun call(fn: LFunction, vararg arguments: LValue, name: String = "cres") = LLVMBuildCall(llvm, fn.llvm, PointerPointer(*arguments), arguments.size, name)

  fun op(instr: String, a: LValue, b: LValue, name: String? = null): LValue {
    val call = ArithmeticOps.map[instr] ?: throw NoSuchElementException("unknown arithmetic instruction $instr")
    return call(llvm, a, b, name ?: instr)
  }
  private object ArithmeticOps {
    val map: Map<String, (LLVMBuilderRef, LValue, LValue, String) -> LValue> = mapOf(
      "add" to ::LLVMBuildAdd, "sub" to ::LLVMBuildSub,
      "mul" to ::LLVMBuildMul, "sdiv" to ::LLVMBuildSDiv
    )
  }
}