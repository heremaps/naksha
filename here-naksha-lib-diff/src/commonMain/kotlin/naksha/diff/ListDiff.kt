package naksha.diff

import naksha.base.ListProxy

class ListDiff: ListProxy<Difference>(Difference::class), Difference {
    var originalLength: Int = 0
    var newLength: Int = 0
}