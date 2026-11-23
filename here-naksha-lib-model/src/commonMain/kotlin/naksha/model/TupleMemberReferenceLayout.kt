package naksha.model

import naksha.jbon.IBook
import naksha.jbon.JbMemberReferenceBuilder
import naksha.jbon.JbMemberReferencePlan
import naksha.model.objects.MemberList
import naksha.model.objects.JsonPath

/** Content-checked, privately owned reference-layout snapshot. No member/proxy/book values are retained; paths, names and actual book positions are checked before reuse, including in-place declaration changes. @since 3.0 */
internal class TupleMemberReferenceLayout(members: MemberList, book: IBook) {
  private class Entry(val name: String, val path: Array<Any?>, val index: Int)
  private val entries: Array<Entry>
  val plan: JbMemberReferencePlan
  val memberCount: Int get() = entries.size

  init {
    val snapshot = ArrayList<Entry>()
    val builder = JbMemberReferenceBuilder()
    for (i in 0 until members.size) {
      val member = members[i] ?: continue
      if (member.isVirtual()) continue
      val name = member.name
      val declared = member.path
      val path = Array<Any?>(declared.size) { declared[it] }
      val index = book.indexOfName(name)
      snapshot.add(Entry(name, path, index))
      builder.add(path, index)
    }
    entries = snapshot.toTypedArray()
    plan = builder.build()
  }

  fun matches(members: MemberList, book: IBook): Boolean {
    var position = 0
    for (i in 0 until members.size) {
      val member = members[i] ?: continue
      if (member.isVirtual()) continue
      if (position >= entries.size) return false
      val entry = entries[position++]
      val name = member.name
      val path = member.path
      if (name != entry.name || path.size != entry.path.size || book.indexOfName(name) != entry.index) return false
      for (segment in 0 until path.size) if (path[segment] != entry.path[segment]) return false
    }
    return position == entries.size
  }

  /** Match metadata already read by extraction rather than rereading every proxy in a second pass. Never checks/caches the member's coercion type or value. @since 3.0 */
  fun matchesEntry(position: Int, name: String, path: JsonPath, index: Int): Boolean {
    if (position >= entries.size) return false
    val entry = entries[position]
    if (name != entry.name || path.size != entry.path.size || index != entry.index) return false
    for (i in 0 until path.size) if (path[i] != entry.path[i]) return false
    return true
  }
}
