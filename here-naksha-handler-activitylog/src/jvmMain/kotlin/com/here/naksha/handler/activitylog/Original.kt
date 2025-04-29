package com.here.naksha.handler.activitylog

import naksha.base.AnyObject
import naksha.base.NullableProperty

/**
 * A subset of data stored under activity log.
 */
class Original : AnyObject() {
    companion object {
        private val STRING_NULL = NullableProperty<Original, String>(String::class)
        private val LONG_NULL = NullableProperty<Original, Long>(Long::class)
    }

    /** The space ID the feature belongs to. */
    var space by STRING_NULL
    /** The timestamp, when a feature was created. */
    var createdAt by LONG_NULL
    /** The timestamp, when a feature was last updated. */
    var updatedAt by LONG_NULL
    /** The previous uuid of the feature. */
    var puuid by STRING_NULL
    /** The merge muuid of the feature. */
    var muuid by STRING_NULL
}