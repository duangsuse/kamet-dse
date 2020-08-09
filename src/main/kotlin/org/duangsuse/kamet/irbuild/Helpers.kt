package org.duangsuse.kamet.irbuild

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage
import org.bytedeco.llvm.global.LLVM.LLVMPointerType
import org.duangsuse.kamet.irbuild.items.LType

typealias Producer<T> = () -> T
typealias Consumer<T> = (T) -> Unit
typealias Pipe<T> = (T) -> T

inline fun withErrorHandling(message: String? = null, op: (BytePointer) -> Int) {
  val szError = BytePointer(null as Pointer?)
  op(szError).also { if (it != 0) szError.asErrorMessage().let { m -> error(if (message != null) "$message: $m" else m) } }
}

fun BytePointer.asErrorMessage(): String = string.also { LLVMDisposeMessage(this) }

fun LType.pointer(): LType = LLVMPointerType(this, 0)

internal fun Boolean.toInt() = if (this) 1 else 0

