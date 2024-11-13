package naksha.diff

class UpdateOp(override val oldValue: Any?, override val newValue: Any?) :
    PrimitiveDiff(oldValue = oldValue, newValue = newValue)
