package naksha.diff

class UpdateOp(override val oldValue: Any?, override val newValue: Any?) :
    PrimitiveDiff(oldValue = oldValue, newValue = newValue) {

    override fun toString(): String {
        return "UpdateOp(oldValue=$oldValue, newValue=$newValue)"
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is UpdateOp && oldValue == other.oldValue && newValue == other.newValue)

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (oldValue?.hashCode() ?: 0)
        result = 31 * result + (newValue?.hashCode() ?: 0)
        return result
    }
}
