package naksha.diff

class UpdateOp(override val oldValue: Any?, override val newValue: Any?) :
    PrimitiveDiff(oldValue = oldValue, newValue = newValue) {

    override fun toString(): String {
        return "UpdateOp(oldValue=$oldValue, newValue=$newValue)"
    }
}
