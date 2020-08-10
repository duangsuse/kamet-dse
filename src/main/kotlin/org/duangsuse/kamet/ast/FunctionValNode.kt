package org.duangsuse.kamet.ast

import org.duangsuse.kamet.Type
import org.duangsuse.kamet.Types
import org.duangsuse.kamet.joinToCommaString
import org.duangsuse.kamet.showIfNotNull

class RootNode(val elements: List<ASTNode>): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = elements.joinToString("\n\n")
}

class PrototypeNode(
  attributes: Attributes,
  val name: String,
  val returnType: Type,
  val parameters: List<Types.TypedName>
): ASTNode {
  val mangled: String
    get() = parameters.joinToString(",") { "${it.type}" }.let { "$name($it):$returnType" }

  val functionName = attributes.mapValueForAST("Prototype", Attribute.NO_MANGLE to name) ?: mangled
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "${Attribute.NO_MANGLE.showIf(functionName != mangled)}$name(${parameters.joinToCommaString()}): $returnType"
}

class FunctionNode(val prototype: PrototypeNode, val body: BlockNode): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "$prototype $body"
}

class BlockNode: ASTNode {
  val elements: MutableList<ASTNode> = mutableListOf()

  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString(): String =
    if (elements.isEmpty()) "{}"
    else elements.flatMap { it.toString().split('\n') }.joinToString("\n") { '\t' + it }.let { "{\n$it\n}" }
}

class ValDeclareNode(
  val name: String,
  val type: Type? = null,
  val value: ASTNode? = null
): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "val $name${type.showIfNotNull(": ")} = $value"
}

class VarDeclareNode(
  val name: String,
  val type: Type? = null,
  val defaultValue: ASTNode? = null,
  val isConst: Boolean = false
): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "var $name${type.showIfNotNull(": ")} = $defaultValue"
}
