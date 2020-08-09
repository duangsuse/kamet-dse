package org.duangsuse.kamet.ast

interface ASTNode {
  fun <R> visitBy(vis: Visitor<R>): R
  interface Visitor<out R> {
    fun see(t: RootNode): R
    fun see(t: StructNode): R
    fun see(t: PrototypeNode): R
    fun see(t: FunctionNode): R
    fun see(t: BlockNode): R
    fun see(t: VarDeclareNode): R
    fun see(t: ValDeclareNode): R
    fun see(t: BinOpNode): R
    fun see(t: UnaryOpNode): R
    fun see(t: SubscriptNode): R
    fun see(t: InvocationNode): R
    fun see(t: TypeRhsNode): R
    fun see(t: IfNode): R
    fun see(t: WhileNode): R
    fun see(t: DoWhileNode): R
    fun see(t: ReturnNode): R
    fun see(t: ConstantNode): R
    fun see(t: NameRefNode): R
    fun see(t: UndefNode): R
  }
}