package org.duangsuse.kamet.irbuild

import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM.*
import org.duangsuse.kamet.irbuild.items.*
import org.duangsuse.kamet.splitPairs

typealias IRBuilderRun<R> = IRBuilder.() -> R

class IRBuilder: LRefContainer<LLVMBuilderRef>(LLVMCreateBuilder()), Disposable {
  var pos: LBasicBlock
    get() = LLVMGetInsertBlock(llvm)
    set(bb) { LLVMPositionBuilderAtEnd(llvm, bb) }
  val function get() = LFunction(LLVMGetBasicBlockParent(pos))
  override fun dispose() { LLVMDisposeBuilder(llvm) }
  inline fun <R> runInside(bb: LBasicBlock, crossinline op: IRBuilderRun<R>): R { pos = bb; return this.op() }
  inline fun <R> runInsideNext(bb: LBasicBlock, then: LBasicBlock, crossinline op: IRBuilderRun<R>): R = runInside(bb, op).also { br(then) }
  inline fun <R> runInsideBack(bb: LBasicBlock, crossinline op: IRBuilderRun<R>): R {
    val oldPos = pos; return runInside(bb, op).also { pos = oldPos }
  }

  // "CBP" icmp, br/condBr/ret, phi
  fun icmp(op: Int, a: LValue, b: LValue, name: String = "cmp") = LLVMBuildICmp(llvm, op, a, b, name)
  fun br(then_bb: LBasicBlock) = LLVMBuildBr(llvm, then_bb)
  fun condBr(cond: LValue, then_bb: LBasicBlock, else_bb: LBasicBlock) = LLVMBuildCondBr(llvm, cond, then_bb, else_bb)
  fun ret(result: LValue) = LLVMBuildRet(llvm, result)
  fun unreachable() = LLVMBuildUnreachable(llvm)
  fun phi(type: LType, vararg branches: Pair<LValue, LBasicBlock>, name: String = "res"): LValue {
    val node = LLVMBuildPhi(llvm, type, name)
    val (vs, bbs) = branches.asIterable().splitPairs { it }
    LLVMAddIncoming(node, PointerPointer(*vs.toTypedArray()), PointerPointer(*bbs.toTypedArray()), vs.size)
    return node
  }

  fun load(value: LValue, name: String = "load") = LLVMBuildLoad(llvm, value, name)
  fun store(value: LValue, dest: LValue) = LLVMBuildStore(llvm, value, dest)
  fun bitCast(value: LValue, type: LType, name: String) = LLVMBuildBitCast(llvm, value, type, name)
  fun cast(kind: Int, value: LValue, type: LType, name: String) = LLVMBuildCast(llvm, kind, value, type, name)
  fun sizeof(type: LType) = LLVMSizeOf(type)
  fun alloca(type: LType, name: String) = LLVMBuildAlloca(llvm, type, name)
  fun call(fn: LFunction, vararg arguments: LValue, name: String = "cres") = LLVMBuildCall(llvm, fn.llvm, PointerPointer(*arguments), arguments.size, name)

  fun allocateLocal(type: LType, name: String? = null): LValue {
    val entryBlock = function.entryBlock
    val allocated = runInsideBack(entryBlock) {
      positAtFirst(entryBlock)
      alloca(type, "obj")
    }
    if (name != null) LLVMSetValueName2(allocated, name, name.length.toLong())
    return allocated
  }
  fun positAtFirst(bb: LBasicBlock) { LLVMPositionBuilder(llvm, bb, LLVMGetFirstInstruction(bb)) }

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