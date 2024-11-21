package naksha.diff

class RemoveOp(override val oldValue: Any?) : PrimitiveDiff(oldValue = oldValue, newValue = null) {

    override fun toString(): String {
        return "RemoveOp(oldValue=$oldValue)"
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is RemoveOp && oldValue == other.oldValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        return result
    }
}