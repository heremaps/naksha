package naksha.base

/**
 * A special interface to improve comparing values.
 */
interface Like {
    /**
     * Tests if this object is like the given value.
     * @param other the other value to compare against.
     * @return _true_ if the other value represents the same as this object; _false_ otherwise.
     */
    fun like(other: Any?): Boolean
}

/**
 * Compare objects using the like method preferable, otherwise use equals.
 */
@Suppress("NOTHING_TO_INLINE")
inline infix fun Any.like(other: Any?): Boolean {
    if (this is Like && this.like(other)) return true
    if (other is Like && other.like(this)) return true
    return this == other
}