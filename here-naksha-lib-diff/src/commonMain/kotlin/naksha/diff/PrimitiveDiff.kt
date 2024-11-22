package naksha.diff

/**
 *  A common type for primitive changes.
 */
abstract class PrimitiveDiff(
    open val oldValue: Any?,
    open val newValue: Any?
): Difference {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PrimitiveDiff

        if (oldValue != other.oldValue) return false
        if (newValue != other.newValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = oldValue?.hashCode() ?: 0
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}