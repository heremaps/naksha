package naksha.diff

import naksha.base.MapProxy

class MapDiff : MapProxy<Any, Difference>(Any::class, Difference::class), Difference