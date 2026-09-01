@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.model.objects.Member
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A mutable map from member name to the list of [IMemberProcessor] instances registered for that member.
 *
 * The map is backed by a [MutableMap] and provides additional convenience methods:
 * - [addProcessor] — adds a processor for a member (skips if already present)
 * - [removeProcessor] — removes a processor for a member
 *
 * Processors are stored in a [MemberProcessorList] and invoked in insertion order.
 * @since 3.0
 */
@JsExport
class MemberProcessorMap : MutableMap<String, MemberProcessorList> {

    private val delegate: MutableMap<String, MemberProcessorList> = mutableMapOf()

    // -------------------------------------------------------------------------
    // Convenience methods
    // -------------------------------------------------------------------------

    /**
     * Add a processor for the member with the given name.
     *
     * If the processor is already registered for this member, the call is a no-op. Processors are invoked in the order in which they were added.
     * @param name The name of the member as specified in the [NakshaCollection][naksha.model.objects.NakshaCollection].
     * @param processor The processor to add.
     * @return this.
     */
    @JsName("addProcessor")
    fun addProcessor(name: String, processor: IMemberProcessor): MemberProcessorMap {
        var list = delegate[name]
        if (list == null) {
            list = MemberProcessorList()
            delegate[name] = list
        }
        if (!list.contains(processor)) {
            list.add(processor)
        }
        return this
    }

    /**
     * Add a processor for the given member.
     *
     * If the processor is already registered for this member, the call is a no-op. Processors are invoked in the order in which they were added.
     * @param member The member for which to add a processor.
     * @param processor The processor to add.
     * @return this.
     */
    @JsName("addMemberProcessor")
    fun addProcessor(member: Member, processor: IMemberProcessor): MemberProcessorMap = addProcessor(member.name, processor)

    /**
     * Add multiple processor for the member with the given name.
     *
     * If a processor is already registered for this member, the call is a no-op. Processors are invoked in the order in which they were added.
     * @param name The name of the member as specified in the [NakshaCollection][naksha.model.objects.NakshaCollection].
     * @param processors The processors to add.
     * @return this.
     */
    @JsName("addProcessors")
    fun addProcessors(name: String, processors: List<IMemberProcessor>): MemberProcessorMap {
        if (processors.isEmpty()) return this
        var list = delegate[name]
        if (list == null) {
            list = MemberProcessorList()
            delegate[name] = list
        }
        for (processor in processors) {
            if (!list.contains(processor)) {
                list.add(processor)
            }
        }
        return this
    }

    /**
     * Add multiple processor for the member with the given name.
     *
     * If a processor is already registered for this member, the call is a no-op. Processors are invoked in the order in which they were added.
     * @param member The member for which to add processors.
     * @param processors The processors to add.
     * @return this.
     */
    @JsName("addMemberProcessors")
    fun addProcessors(member: Member, processors: List<IMemberProcessor>): MemberProcessorMap = addProcessors(member.name, processors)

    /**
     * Remove a processor for the member with the given name.
     *
     * If the list becomes empty after removal, the entry is removed from the map.
     * @param name The name of the member as specified in the [NakshaCollection][naksha.model.objects.NakshaCollection]..
     * @param processor The processor to remove.
     * @return _true_ if the processor was found and removed, _false_ otherwise.
     */
    @JsName("removeProcessor")
    fun removeProcessor(name: String, processor: IMemberProcessor): Boolean {
        val list = delegate[name] ?: return false
        val removed = list.remove(processor)
        if (list.isEmpty()) {
            delegate.remove(name)
        }
        return removed
    }

    /**
     * Remove a processor for the given member.
     *
     * If the list becomes empty after removal, the entry is removed from the map.
     * @param member The member for which to remove the processor.
     * @param processor The processor to remove.
     * @return _true_ if the processor was found and removed, _false_ otherwise.
     */
    @JsName("removeMemberProcessor")
    fun removeProcessor(member: Member, processor: IMemberProcessor): Boolean = removeProcessor(member.name, processor)

    /**
     * Returns the list of processors for the member with the given name, or `null` if none are registered.
     *
     * Mutation of the returned list will modify the processor map too.
     * @param name The member name.
     * @return the processor list or `null`, if no processor for the given name is added.
     */
    @JsName("getProcessors")
    fun getProcessors(name: String): MemberProcessorList? = delegate[name]

    /**
     * Returns the list of processors for the member or `null` if none are registered.
     *
     * Mutation of the returned list will modify the processor map too.
     * @param member The member.
     * @return the processor list or `null`, if no processor for the given member is added.
     */
    @JsName("getMemberProcessors")
    fun getProcessors(member: Member): MemberProcessorList? = delegate[member.name]

    // -------------------------------------------------------------------------
    // MutableMap delegation
    // -------------------------------------------------------------------------

    override val size: Int
        get() = delegate.size

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun containsKey(key: String): Boolean = delegate.containsKey(key)

    override fun containsValue(value: MemberProcessorList): Boolean = delegate.containsValue(value)

    override fun get(key: String): MemberProcessorList? = delegate[key]

    override fun put(key: String, value: MemberProcessorList): MemberProcessorList? = delegate.put(key, value)

    override fun remove(key: String): MemberProcessorList? = delegate.remove(key)

    override fun putAll(from: Map<out String, MemberProcessorList>) {
        delegate.putAll(from)
    }

    override fun clear() {
        delegate.clear()
    }

    override val keys: MutableSet<String>
        get() = delegate.keys

    override val values: MutableCollection<MemberProcessorList>
        get() = delegate.values

    override val entries: MutableSet<MutableMap.MutableEntry<String, MemberProcessorList>>
        get() = delegate.entries

    /**
     * Returns `true` if there are no processors registered for any member.
     */
    fun isEmptyProcessors(): Boolean = delegate.isEmpty()

    /**
     * Create a backup of the current processor map, useful when temporary different processors are needed.
     * @param clear if the map should be cleared after backup, makes the backup faster.
     * @return the backup of the processor map.
     * @since 3.0
     */
    fun backup(clear: Boolean = true): Map<String, MemberProcessorList> {
        val backup = mutableMapOf<String, MemberProcessorList>()
        for ((key, value) in delegate.entries) {
            // When we clear, we can just copy the reference, otherwise we need a copy of the MemberList.
            if (clear) backup[key] = value else backup[key] = value.copy()
        }
        if (clear) delegate.clear()
        return backup
    }

    /**
     * Restores a backup, useful when temporary different processors are needed.
     * @param backup the backup to restore.
     * @param clear if the map should be cleared, before restoring.
     * @param consume if the backup should be consumed, then only references need to be copied.
     * @return this.
     */
    fun restore(backup: Map<String, MemberProcessorList>, clear: Boolean = true, consume: Boolean = false): MemberProcessorMap {
        if (clear) delegate.clear()
        for ((key, backupList) in backup) {
            if (clear && consume) {
                // We can copy the reference, the backup is not need anymore anyway.
                delegate[key] = backupList
            } else if (clear) {
                // Copy from backup, because the backup should stay intact and may be reused.
                // If we would not do this, any modification after restore would modify the backup too!
                delegate[key] = backupList.copy()
            } else { // no clear, no consume
                val existing = delegate[key]
                if (existing == null) {
                    if (consume) delegate[key] = backupList else  delegate[key] = backupList.copy()
                } else {
                    for (e in backupList) {
                        if (!existing.contains(e)) existing.add(e)
                    }
                }
            }
        }
        return this
    }
}
