package naksha.base

/**
 * A cross-platform thread local, in Java extends `ThreadLocal`, in JavaScript it is a pseudo value wrapping object.
 * @since 3.0
 */
expect open class ThreadLocalNullable<T>(initializer: (()->T?)?) {
    /**
     * Returns the thread-local value.
     */
    fun get(): T?

    /**
     * Sets the thread-local.
     * @param value The value to set.
     */
    fun set(value: T?)
}