package com.here.naksha.lib.core.models.naksha

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass

class SpaceList : ListProxy<Space>(Space.TYPE) {
    companion object SpaceList_C {
        @JvmField
        val TYPE = forKClass(SpaceList::class)
    }
}