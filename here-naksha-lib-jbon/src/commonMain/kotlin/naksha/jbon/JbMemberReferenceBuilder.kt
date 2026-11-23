package naksha.jbon

import naksha.base.ListProxy
import naksha.base.illegalArg
import kotlin.js.JsExport
import kotlin.math.floor

/** Builds an immutable reference plan using flat arrays instead of an object/map per path prefix. Duplicate paths retain the first declaration. Builders may be reused; build copies only populated entries. @since 3.0 */
@JsExport
class JbMemberReferenceBuilder {
  private var keys: Array<Any?> = arrayOfNulls(8)
  private var firstChildren = IntArray(8) { -1 }
  private var nextSiblings = IntArray(8) { -1 }
  private var indexes = IntArray(8) { -1 }
  private var size = 1

  /** Add an array path. Index -1 records a deferred missing-book error, raised only if encoding encounters that path. @since 3.0 */
  fun add(path: Array<Any?>, memberIndex: Int): JbMemberReferenceBuilder {
    checkIndex(memberIndex)
    var node = 0
    for (key in path) node = descend(node, key)
    finish(node, memberIndex)
    return this
  }

  /** Add a platform-list path without allocating an intermediate array. Only String/Int segments are allowed. @since 3.0 */
  fun addList(path: ListProxy<*>, memberIndex: Int): JbMemberReferenceBuilder {
    checkIndex(memberIndex)
    var node = 0
    for (i in 0 until path.size) node = descend(node, path[i])
    finish(node, memberIndex)
    return this
  }

  /** Return a privately owned snapshot; subsequent builder changes cannot modify an existing plan. @since 3.0 */
  fun build(): JbMemberReferencePlan = JbMemberReferencePlan.create(keys, firstChildren, nextSiblings, indexes, size)

  private fun checkIndex(index: Int) {
    val raw: Any? = index
    if (raw !is Int) throw illegalArg("Member reference index must be an int32 integer")
    val value = (raw as Number).toDouble()
    if (value < -1.0 || value > Int.MAX_VALUE.toDouble() || floor(value) != value) {
      throw illegalArg("Member reference index must be a nonnegative int32 integer or -1 for a missing book entry")
    }
  }

  private fun finish(node: Int, index: Int) {
    if (indexes[node] == -1) indexes[node] = if (index == -1) -2 else index
  }

  private fun descend(parent: Int, key: Any?): Int {
    if (key !is String && key !is Int) throw illegalArg("Member reference path must contain only strings and integers")
    if (key is Int) {
      // JavaScript numeric runtime checks cannot distinguish fractional numbers from Int by type alone.
      val value = (key as Number).toDouble()
      if (value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble() || floor(value) != value) {
        throw illegalArg("Member reference array position must be a finite int32 integer")
      }
    }
    var node = firstChildren[parent]
    while (node >= 0) {
      if (keys[node] == key) return node
      node = nextSiblings[node]
    }
    if (size == keys.size) {
      val old = size
      keys = keys.copyOf(old * 2)
      firstChildren = firstChildren.copyOf(old * 2)
      nextSiblings = nextSiblings.copyOf(old * 2)
      indexes = indexes.copyOf(old * 2)
      for (i in old until keys.size) {
        firstChildren[i] = -1
        nextSiblings[i] = -1
        indexes[i] = -1
      }
    }
    node = size++
    keys[node] = key
    nextSiblings[node] = firstChildren[parent]
    firstChildren[parent] = node
    return node
  }
}
