package org.duangsuse.kamet.ast

import org.duangsuse.kamet.Type
import org.duangsuse.kamet.Types

class ConstantNode(val type: Types.Prim, val value: String) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = value
}

class NameRefNode(val name: String) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = name
}

class UndefNode(val type: Type) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "(undefined as $type)"
}