package org.duangsuse.kamet.ast

// arithmetic and boolean operators
interface Operator { val symbol: String; val precedence: Int }

private var nextPrecedence = 1
enum class BinOp(override val symbol: String, val isLogical: Boolean, override val precedence: Int): Operator {
  AccessMember("."), AsCast("as"),
  Times(+"*"), Div("/"), Rem("%"),
  Plus(+"+"), Minus("-"),
  Shl(+"<<"), Shr(">>"),
  CmpLT(+"<", true), CmpLE/*NGT*/("<=", true), CmpGT(">", true), CmpGE/*NLT*/(">=", true),
  Equals(+"==", true), NEquals("!=", true),
  BitAnd(+"&"), BitXor(+"^"), BitOr(+"|"),
  And(+"&&", true), Or(+"||", true),
  Assign(+"=");
  constructor(symbol: String, isLogical: Boolean = false): this(symbol, isLogical, nextPrecedence)
  constructor(symbol: NextPrecedence, isLogical: Boolean = false): this(symbol.text, isLogical, nextPrecedence++)
  companion object {
    val assignable = listOf(Plus,Minus,Times,Div,Rem, BitAnd,BitOr,BitXor, Shl,Shr)
    private operator fun String.unaryPlus() = NextPrecedence(this)
    class NextPrecedence(val text: String)
  }
}
enum class UnaryOp(override val symbol: String, val hasPostfix: Boolean = false): Operator {
  UnaryMinus("-"), Not("!"), Inverse("~"),
  Inc("++", true), Dec("--", true),
  Indirection("*"), AddressOf("&");
  override val precedence: Int = 0
}

class BinOpNode(val lhs: ASTNode, val rhs: ASTNode, val op: BinOp): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = "($lhs ${op.symbol} $rhs)"
}

class UnaryOpNode(val op: UnaryOp, val value: ASTNode, val isPostfix: Boolean = false): ASTNode {
  override fun <R> visitBy(vis: ASTNode.Visitor<R>) = vis.see(this)
  override fun toString() = if (isPostfix) "$value${op.symbol}" else "${op.symbol}$value"
}
