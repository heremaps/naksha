package naksha.diff

class InsertOp(override val newValue: Any?): PrimitiveDiff(oldValue = null, newValue){

    override fun toString(): String {
        return "InsertOp(newValue=$newValue)"
    }
}