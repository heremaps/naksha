package naksha.diff

class RemoveOp(override val oldValue: Any?) : PrimitiveDiff(oldValue = oldValue, newValue = null){

    override fun toString(): String {
        return "RemoveOp(oldValue=$oldValue)"
    }
}