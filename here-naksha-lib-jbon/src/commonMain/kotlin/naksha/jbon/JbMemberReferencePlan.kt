package naksha.jbon

import naksha.base.internalError
import kotlin.js.JsExport

/** Immutable, compact path-to-members-book mapping. Arrays are privately owned copies; the plan contains no mutable member declarations, book values or encoder traversal state. @since 3.0 */
@JsExport
class JbMemberReferencePlan private constructor(
  private val keys: Array<Any?>,
  private val firstChildren: IntArray,
  private val nextSiblings: IntArray,
  private val indexes: IntArray
) {
  internal companion object {
    fun create(keys: Array<Any?>, children: IntArray, siblings: IntArray, indexes: IntArray, size: Int): JbMemberReferencePlan =
      JbMemberReferencePlan(keys.copyOf(size), children.copyOf(size), siblings.copyOf(size), indexes.copyOf(size))
  }

  internal fun child(parent: Int, key: Any?): Int {
    var node = firstChildren[parent]
    while (node >= 0) {
      if (keys[node] == key) return node
      node = nextSiblings[node]
    }
    return -1
  }

  internal fun reference(node: Int): Int {
    val index = indexes[node]
    if (index == -2) throw internalError("Member index should not be negative at this point")
    return index
  }

  /** Query an exact path without mutating the plan. Prefixes, string keys and integer positions remain distinct. @since 3.0 */
  fun memberIndex(path: Array<Any?>): Int {
    var node = 0
    for (key in path) {
      val child = child(node, key)
      if (child < 0) return -1
      node = child
    }
    return reference(node)
  }
}
