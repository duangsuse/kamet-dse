package org.duangsuse.kamet.ast

import org.duangsuse.kamet.Type
import org.duangsuse.kamet.joinToCommaString

// Pointer subscript, invocation, and type rhs (as, sizeof)
class SubscriptNode(val array: ASTNode, val index: ASTNode): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "$array[$index]"
}

class InvocationNode(val name: String, val elements: List<ASTNode>): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "$name(${elements.joinToCommaString()})"
}

class TypeRhsNode(val value: ASTNode, val type: Type, val op: String): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "($value$op$type)"
}
