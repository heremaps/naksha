package naksha.diff

import naksha.base.ListProxy

class ListDiff: ListProxy<Difference>(Difference::class), Difference {
    // TODO: clean this up?
    var originalLength: Int = 0
    var newLength: Int = 0
}