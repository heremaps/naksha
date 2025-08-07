package com.here.naksha.handler.activitylog

import naksha.base.AnyObject
import naksha.base.Long_TYPE
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.String_TYPE

/**
 * A subset of data stored under activity log.
 */
class Original : AnyObject() {
    companion object {
        public val TYPE = forKClass<Original>(Original::class).withPackageName("com.here.naksha.handler.activitylog")
        private val STRING_NULL = NullableProperty<Original, String>(String_TYPE)
        // TODO: We should never work with LONG, we should use Int64, JavaScript does not have 64-bit integers !!!
        private val LONG_NULL = NullableProperty<Original, Long>(Long_TYPE)
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