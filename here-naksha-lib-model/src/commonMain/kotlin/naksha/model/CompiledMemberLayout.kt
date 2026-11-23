@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Platform
import naksha.base.illegalState
import naksha.jbon.JbMemberReferenceBuilder
import naksha.jbon.JbMemberReferencePlan
import naksha.jbon.MemberBookLayout
import naksha.model.objects.JsonPath
import naksha.model.objects.Member
import naksha.model.objects.MemberList
import naksha.model.objects.MemberType
import naksha.model.objects.StandardMembers
import kotlin.js.JsExport

/** Collection-owned, privately copied declaration snapshot. No live declarations, tuple values or encoder traversal state are cached. Replacement collection descriptors compile a new instance. @since 3.0 */
@JsExport
class CompiledMemberLayout(members: MemberList) {
  private val declarations: MemberList = (Platform.copy(members.platformObject(), true)
    ?: throw illegalState("Failed to copy member declarations")).proxy(MemberList::class)
  private val entries: Array<Entry>
  private val memberToBook: IntArray
  private val originPath: JsonPath?
  val bookLayout: MemberBookLayout
  val references: JbMemberReferencePlan
  val size: Int get() = entries.size

  internal class Entry(val memberIndex: Int, val name: String, val type: MemberType, val path: JsonPath)

  init {
    declarations.validateAndSort()
    // Populate the copied list's name cache before publication; encoding only reads it.
    declarations.useMember(StandardMembers.Id)
    declarations.useMember(StandardMembers.Tn)
    declarations.useMember(StandardMembers.NextVersion)
    declarations.useMember(StandardMembers.GlobalBookFeatureNumber)
    originPath = declarations.findMember(StandardMembers.Origin)?.path?.let { copyPath(it) }
    val stored = ArrayList<Entry>()
    memberToBook = IntArray(declarations.size) { -1 }
    val builder = JbMemberReferenceBuilder()
    for (i in 0 until declarations.size) {
      val member = declarations[i] ?: throw illegalState("Member at $i is null")
      if (member.isVirtual()) continue
      // Processor-visible proxy paths are separate from the private compiled paths.
      val path = copyPath(member.path)
      val slot = stored.size
      memberToBook[i] = slot
      val entry = Entry(i, member.name, member.dataType, path)
      stored.add(entry)
      builder.add(Array<Any?>(path.size) { path[it] }, slot)
    }
    entries = stored.toTypedArray()
    bookLayout = MemberBookLayout(Array(entries.size) { entries[it].name })
    references = builder.build()
  }

  /** Read stable slot metadata without exposing mutable declarations to storage callers. @since 3.0 */
  fun nameAt(slot: Int): String = entries[slot].name

  /** Read the compiled coercion/conversion type. @since 3.0 */
  fun typeAt(slot: Int): MemberType = entries[slot].type

  /** Resolve a declaration index, including virtual members with no stored slot. @since 3.0 */
  fun bookSlotForMember(index: Int): Int = memberToBook.getOrNull(index) ?: -1

  internal fun entryAt(slot: Int): Entry = entries[slot]

  internal fun pathForName(name: String): JsonPath? {
    if (name == StandardMembers.ORIGIN) return originPath
    val slot = bookLayout.indexOfName(name)
    return if (slot >= 0) entries[slot].path else null
  }

  /** Processor arguments retain their existing read-only contract. Their copied proxy paths are never the privately owned paths used for extraction/reference routing; no copy of the whole declaration graph is allocated per feature. @since 3.0 */
  internal fun encodingMembers(): MemberList = declarations

  private fun copyPath(source: JsonPath): JsonPath {
    source.validate()
    val copy = JsonPath()
    for (segment in source) copy.add(segment)
    return copy
  }
}
