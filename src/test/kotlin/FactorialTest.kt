import org.bytedeco.llvm.global.LLVM.LLVMIntEQ
import org.duangsuse.kamet.irbuild.*
import org.duangsuse.kamet.irbuild.items.LModule
import org.duangsuse.kamet.irbuild.items.LPassManager
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals


class FactorialTest {
  @Test fun run() {
     // Used to retrieve messages from functions
    LModule.setupLLVMNative()
    val mod = LModule("mod-fac")
    val fac = mod.addFunction("fac", LTypes.run { fnTyped(i32, i32) })
    val n = fac[0]

    val entry = fac.basicBlock("entry")
    val thenBB = fac.basicBlock("then")
    val elseBB = fac.basicBlock("else")
    val mergeBB = fac.basicBlock("merge")
    val ir = IRBuilder()

    val cond = ir.runInside(entry) { icmp(LLVMIntEQ, n, LValues.constInt(LTypes.i32, 0)) }
    ir.condBr(cond, thenBB, elseBB)

    val rThenBB = ir.runInsideNext(thenBB, mergeBB) {
      LValues.constInt(LTypes.i32, 1)
    }

    val rElseBB = ir.runInsideNext(elseBB, mergeBB) {
      val rFac = ir.call(fac, ir.op("sub", n, LValues.constInt(LTypes.i32, 1), "dec"))
      ir.op("mul", n, rFac)
    }

    val res = ir.runInside(mergeBB) {
      ir.phi(LTypes.i32, rThenBB to thenBB, rElseBB to elseBB)
    }
    ir.ret(res)

    mod.runVerify()
    // Handler == LLVMAbortProcessAction -> No need to check errors

    try {
      mod.createJITCompiler(2)
    } catch (e: Error) { System.err.println(e.message); exitProcess(-1) }

    val pass = LPassManager()
    LPassManager.CommonPasses.run {
      pass.add(constProp, instCombine, mem2reg)
      pass.add(gvn, cfgSimplify)
    }
    pass.runOn(mod)
    mod.debugDump()

    try {
      assertEquals(3628800,
        mod.runFunction(fac, LValues.genericInt(LTypes.i32, 10)).int)
    } finally {
      pass.dispose()
      ir.dispose()
      mod.dispose()
    }
  }
}