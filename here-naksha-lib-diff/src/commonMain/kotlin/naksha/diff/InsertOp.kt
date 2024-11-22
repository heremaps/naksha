package naksha.diff

class InsertOp(override val newValue: Any?) : PrimitiveDiff(oldValue = null, newValue) {

    override fun toString(): String {
        return "InsertOp(newValue=$newValue)"
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is InsertOp && newValue == other.newValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}