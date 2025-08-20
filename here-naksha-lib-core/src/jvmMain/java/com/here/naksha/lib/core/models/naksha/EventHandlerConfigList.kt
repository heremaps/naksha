package com.here.naksha.lib.core.models.naksha

import naksha.base.ListProxy
import naksha.base.MutableInt
import naksha.base.Platform.Platform_C.forKClass

class EventHandlerConfigList : ListProxy<EventHandlerConfig>(EventHandlerConfig.TYPE) {
    companion object EventHandlerConfigList_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] of the [EventHandlerConfigList].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(EventHandlerConfigList::class)
    }

    /**
     * Order the event-handler configurations in this list the same as they are ordered in [Space.getEventHandlerIds]. If this list contains `null` values, they will be ordered at the end.
     *
     * @param space The space
     * @param removeNulls If explicitly set to _true_, the `null` values at the end will be truncated.
     * @return this.
     * @since 3.0
     */
    @JvmOverloads
    fun orderBySpace(space: Space, removeNulls: Boolean = false): EventHandlerConfigList {
        val spaceEventHandlerIds = space.eventHandlerIds
        sortWith { a, b ->
            // sort `null` last
            if (a === b) return@sortWith 0
            if (a == null) return@sortWith 1 // a is bigger than b
            if (b == null) return@sortWith -1 // a is smaller than b

            val ai = spaceEventHandlerIds.indexOf(a.id)
            val bi = spaceEventHandlerIds.indexOf(b.id)
            if (ai < bi) -1 else if (ai > bi) 1 else 0
        }
        if (removeNulls) {
            var nulls = 0
            for (i in size - 1 downTo 0) {
                val handler = this[i]
                if (handler == null) {
                    nulls++
                } else break // nulls are only at the end
            }
            if (nulls > 0) {
                this.size -= nulls
            }
        }
        return this
    }
}