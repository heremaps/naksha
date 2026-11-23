package naksha.model

import naksha.base.internalError
import naksha.jbon.IBook
import naksha.model.objects.MemberList

/** Private, per-encoding immutable route snapshot. Built after member processing so raw/in-place declaration changes are observed on the next call, without retaining mutable proxies or caching public validation. @since 3.0 */
internal class TupleMemberRouting(members: MemberList, book: IBook) {
  private class Node {
    // Most nodes are leaves: allocate child maps only when a path actually continues.
    var children: HashMap<Any?, Node>? = null
    var index: Int = -1
  }

  private val root = Node()

  init {
    for (i in 0 until members.size) {
      val member = members[i] ?: continue
      if (member.isVirtual()) continue
      val name = member.name
      val path = member.path
      var node = root
      for (segment in 0 until path.size) {
        val key = path[segment]
        var children = node.children
        if (children == null) {
          children = HashMap(1)
          node.children = children
        }
        var child = children[key]
        if (child == null) {
          child = Node()
          children[key] = child
        }
        node = child
      }
      // Duplicate paths select the first non-virtual declaration, exactly as the former linear callback did. Prefix routes do not replace longer routes.
      if (node.index == -1) {
        val index = book.indexOfName(name)
        // -1 is no route; -2 is an encountered declaration with no book value. Do not let a later duplicate path replace that first declaration.
        node.index = if (index < 0) -2 else index
      }
    }
  }

  fun find(path: Array<Any?>, end: Int): Int {
    var node = root
    for (i in 0 until end) node = node.children?.get(path[i]) ?: return -1
    val index = node.index
    if (index == -1) return -1
    // A member processor may have changed declarations after extraction. Match the old callback's failure timing: an absent book entry is an error only if that path is encountered.
    if (index < 0) throw internalError("Member index should not be negative at this point")
    return index
  }
}
