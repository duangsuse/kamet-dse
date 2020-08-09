package org.duangsuse.kamet.ast

typealias Attributes = Set<Attribute>
enum class Attribute {
  PACKED, NO_MANGLE;

  companion object {
    private val attributeMap by lazy { values().associateBy { it.name.toLowerCase() } }
    fun lookup(name: String): Attribute? = attributeMap[name]
  }

  fun notApplicableTo(what: String): Nothing = error("attribute \"$this\" is not applicable to $what")
}

fun <R> Attributes.mapValueForAST(name: String, vararg pairs: Pair<Attribute, R>): R? {
  val map = mapOf(*pairs)
  for (attr in this) map[attr]?.let { return it } ?: attr.notApplicableTo(name)
  return null
}