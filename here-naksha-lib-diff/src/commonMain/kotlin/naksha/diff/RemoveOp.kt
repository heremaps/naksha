package naksha.diff

class RemoveOp(override val oldValue: Any?) : PrimitiveDiff(oldValue = oldValue, newValue = null){

    override fun toString(): String {
        return "RemoveOp(oldValue=$oldValue)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as RemoveOp

        return oldValue == other.oldValue
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        return result
    }
}