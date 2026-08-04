package naksha.diff

import naksha.base.PTypedMap

class MapDiff : PTypedMap<Any, Difference>(Any::class, Difference::class), Difference