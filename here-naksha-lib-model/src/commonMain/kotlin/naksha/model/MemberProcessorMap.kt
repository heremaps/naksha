@file:Suppress("OPT_IN_USAGE")

package naksha.model

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
open class MemberProcessorMap : MutableMap<String, MemberProcessorList> {

    private val delegate: MutableMap<String, MemberProcessorList> = mutableMapOf()

    // -------------------------------------------------------------------------
    // Convenience methods
    // -------------------------------------------------------------------------

    /**
     * Add a processor for the given member name.
     *
     * If the processor is already registered for this member, the call is a no-op.
     * Processors are invoked in the order in which they were added.
     * @param name The name of the member as specified in the collection.
     * @param processor The processor to add.
     */
    @JsName("addProcessor")
    open fun addProcessor(name: String, processor: IMemberProcessor) {
        var list = delegate[name]
        if (list == null) {
            list = MemberProcessorList()
            delegate[name] = list
        }
        if (!list.contains(processor)) {
            list.add(processor)
        }
    }

    /**
     * Remove a processor for the given member name.
     *
     * If the list becomes empty after removal, the entry is removed from the map.
     * @param name The name of the member as specified in the collection.
     * @param processor The processor to remove.
     * @return `true` if the processor was found and removed, `false` otherwise.
     */
    @JsName("removeProcessor")
    open fun removeProcessor(name: String, processor: IMemberProcessor): Boolean {
        val list = delegate[name] ?: return false
        val removed = list.remove(processor)
        if (list.isEmpty()) {
            delegate.remove(name)
        }
        return removed
    }

    /**
     * Returns the list of processors for the given member name, or `null` if none are registered.
     * @param name The member name.
     */
    @JsName("getProcessors")
    open fun getProcessors(name: String): MemberProcessorList? = delegate[name]

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
}
