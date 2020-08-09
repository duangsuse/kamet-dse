package org.duangsuse.kamet.ast

class ReturnNode(val value: ASTNode) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "return $value"
}