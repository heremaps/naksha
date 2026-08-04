@file:OptIn(ExperimentalJsStatic::class)

// This will be exposed
// - in JavaScript at the namespace: naksha.model.{name}
// - jn Java at the class naksha.model.LibModelKt.{name}
package naksha.model

import naksha.base.Int64
import naksha.base.BaseUtil.BaseUtil_C.MILLISECOND
import naksha.base.BaseUtil.BaseUtil_C.MILLIS_TO_MICROS
import kotlin.js.ExperimentalJsStatic
import kotlin.jvm.JvmField

@Deprecated("Replace with Action enumeration class",
    replaceWith = ReplaceWith("Action.CREATED.intValue"),
    level = DeprecationLevel.WARNING)
const val ACTION_CREATE = 0
@Deprecated("Replace with Action enumeration class",
    replaceWith = ReplaceWith("Action.UPDATED.intValue"),
    level = DeprecationLevel.WARNING)
const val ACTION_UPDATE = 1
@Deprecated("Replace with Action enumeration class",
    replaceWith = ReplaceWith("Action.DELETED.intValue"),
    level = DeprecationLevel.WARNING)
const val ACTION_DELETE = 2

/**
 * The default latency to use for storages, being 200,000 microseconds (aka 200 milliseconds).
 * @since 3.0
 */
@JvmField
val LATENCY_STORAGE = Int64(200) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for S3 buckets, being 100,000 microseconds (aka 100 milliseconds).
 * @since 3.0
 */
@JvmField
val LATENCY_S3 = Int64(100) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for Redis including some network latency, being 10,000 microseconds (aka 10 milliseconds).
 * @since 3.0
 */
@JvmField
val LATENCY_REDIS_REMOTE = Int64(10) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for a local Redis or with ultra-fast networking, being 1,000 microseconds (aka 1 millisecond).
 * @since 3.0
 */
@JvmField
val LATENCY_REDIS_LOCAL = Int64(1) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for in-memory caching, being 0 microsecond.
 * @since 3.0
 */
@JvmField
val LATENCY_MEMORY = Int64(0)

/**
 * The default feature encoding to be used by all storages — see [Naksha.DEFAULT_DATA_ENCODING].
 * @since 3.0.0
 */
@Deprecated("Replaced with official DEFAULT_DATA_ENCODING in Naksha",
    replaceWith = ReplaceWith("Naksha.DEFAULT_DATA_ENCODING"),
    level = DeprecationLevel.WARNING)
@JvmField
val DEFAULT_DATA_ENCODING = Naksha.DEFAULT_DATA_ENCODING
