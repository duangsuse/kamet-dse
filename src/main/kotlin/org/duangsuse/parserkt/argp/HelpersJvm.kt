package org.duangsuse.parserkt.argp

import java.io.File

val noFile = File("")
/** Value [noFile] is provided by default. [flags]: mode "rw"/"d" (dir), create "+" */
fun argFile(name: String, help: String, param: String? = "path", default_value: File? = noFile, repeatable: Boolean = false, flags: String = "r")
  = arg(name, help, param, default_value, repeatable) {
  val file = File(it)
  fun require(mode: String, predicate: (File) -> Boolean) = require(predicate(file)) {"file \"$file\" cannot be opened $mode"}
  val isDir = ('d' in flags)
  if (isDir) require("as dir", File::isDirectory)
  else {
    if ('r' in flags) require("read", File::canRead)
    if ('w' in flags) require("write", File::canWrite)
  }
  if ('+' in flags && !file.exists()) {
    if (isDir) file.mkdirs()
    else { File(file.parent).mkdirs() ; file.createNewFile() }
  }
  file
}
