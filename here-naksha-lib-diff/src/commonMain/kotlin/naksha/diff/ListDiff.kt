package naksha.diff

import naksha.base.PTypedArray

class ListDiff: PTypedArray<Difference>(Difference::class), Difference {
    var originalLength: Int = 0
    var newLength: Int = 0
}