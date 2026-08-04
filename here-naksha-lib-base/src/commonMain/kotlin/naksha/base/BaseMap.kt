package naksha.base

/**
 * A map where keys are [Literal]'s.
 *
 * @since 3.0
 * @see Literal.literal
 */
expect class BaseMap() : BaseObject, IMutableMap {
    override fun set(key: Literal, value: Any?)
    override fun remove(key: Literal): Boolean
    override fun delete(key: Literal): Any?
    override fun setIfAbsent(key: Literal, value: Any?): Any?
    override fun compareAndSet(key: Literal, expectedValue: Any?, newValue: Any?): Boolean
    override fun deleteIf(key: Literal, expectedValue: Any): Any?
    override fun replace(key: Literal, expectedValue: Any, newValue: Any?): Boolean
    override fun removeIf(key: Literal, expectedValue: Any): Boolean
    override fun clear()
    override val length: Int
    override fun forEach(action: (key: Literal, value: Any?) -> Unit)
    override fun <R> reduce(initialValue: R?, action: (key: Literal, value: Any?, result: R?) -> R?): R?
    override fun get(key: Literal): Any?
    override fun containsKey(key: Literal): Boolean
}