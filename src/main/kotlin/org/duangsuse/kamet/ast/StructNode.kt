package org.duangsuse.kamet.ast

import org.duangsuse.kamet.Type
import org.duangsuse.kamet.joinToCommaString

class StructNode(attributes: Attributes, val name: String, val elements: List<Pair<String, Type>>): ASTNode {
  val isPacked = attributes.mapValueForAST("Struct", Attribute.PACKED to true) ?: false
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "${Attribute.PACKED.showIf(isPacked)}struct $name { ${elements.joinToCommaString()} }"
}