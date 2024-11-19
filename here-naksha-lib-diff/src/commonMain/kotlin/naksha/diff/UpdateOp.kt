package naksha.diff

class UpdateOp(override val oldValue: Any?, override val newValue: Any?) :
    PrimitiveDiff(oldValue = oldValue, newValue = newValue) {

    override fun toString(): String {
        return "UpdateOp(oldValue=$oldValue, newValue=$newValue)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as UpdateOp

        if (oldValue != other.oldValue) return false
        if (newValue != other.newValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}
