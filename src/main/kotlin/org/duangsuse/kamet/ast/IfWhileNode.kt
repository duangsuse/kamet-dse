package org.duangsuse.kamet.ast

class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = if (elseBlock == null) "if $condition $thenBlock"
    else "if ($condition) $thenBlock else $elseBlock"
}

class WhileNode(val condition: ASTNode, val block: ASTNode) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "while ($condition) $block"
}

class DoWhileNode(val block: ASTNode, val condition: ASTNode) : ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "do $block while ($condition)"
}
