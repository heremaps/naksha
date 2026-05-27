@file:OptIn(ExperimentalJsStatic::class)

// This will be exposed
// - in JavaScript at the namespace: naksha.model.{name}
// - jn Java at the class naksha.model.LibModelKt.{name}
package naksha.model

import naksha.base.Int64
import naksha.base.PlatformUtil.PlatformUtilCompanion.MILLISECOND
import naksha.base.PlatformUtil.PlatformUtilCompanion.MILLIS_TO_MICROS
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsStatic
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
@JsStatic
val LATENCY_STORAGE = Int64(200) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for S3 buckets, being 100,000 microseconds (aka 100 milliseconds).
 * @since 3.0
 */
@JvmField
@JsStatic
val LATENCY_S3 = Int64(100) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for Redis including some network latency, being 10,000 microseconds (aka 10 milliseconds).
 * @since 3.0
 */
@JvmField
@JsStatic
val LATENCY_REDIS_REMOTE = Int64(10) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for a local Redis or with ultra-fast networking, being 1,000 microseconds (aka 1 millisecond).
 * @since 3.0
 */
@JvmField
@JsStatic
val LATENCY_REDIS_LOCAL = Int64(1) * MILLISECOND * MILLIS_TO_MICROS

/**
 * The default latency to use for in-memory caching, being 0 microsecond.
 * @since 3.0
 */
@JvmField
@JsStatic
val LATENCY_MEMORY = Int64(0)

/**
 * The default flags to be used by all storages — see [Naksha.DEFAULT_FLAGS].
 * @since 3.0.0
 */
@Deprecated("Replaced with official DEFAULT_FLAGS in Naksha",
    replaceWith = ReplaceWith("Naksha.DEFAULT_FLAGS"),
    level = DeprecationLevel.WARNING)
@JvmField
@JsStatic
val DEFAULT_FLAGS = Naksha.DEFAULT_FLAGS

/**
 * The [meta][Tuple.meta] bit.
 * @since 3.0.0
 */
const val META_BIT: FetchMode = 1

/**
 * The bitmask to AND apply to clear the _meta_ bit.
 * @since 3.0.0
 */
const val META_CLEAR: FetchMode = META_BIT.inv()

/**
 * The _geometry_ bit, covering [geometry][Tuple.geo] and the [reference-point][Tuple.referencePoint].
 * @since 3.0.0
 */
const val GEOMETRY_BIT: FetchMode = 2

/**
 * The bitmask to AND apply to clear the _geometry_ bit.
 * @since 3.0.0
 */
const val GEOMETRY_CLEAR: FetchMode = GEOMETRY_BIT.inv()

/**
 * The _feature_ bit, covers [feature][Tuple.feature] and [tags][Tuple.tags].
 * @since 3.0.0
 */
const val FEATURE_BIT: FetchMode = 4

/**
 * The bitmask to AND apply to clear the _feature_ bit.
 * @since 3.0.0
 */
const val FEATURE_CLEAR: FetchMode = FEATURE_BIT.inv()

/**
 * The [attachment][Tuple.attachment] bit.
 * @since 3.0.0
 */
const val ATTACHMENT_BIT: FetchMode = 8

/**
 * The bitmask to AND apply to clear the _attachment_ bit.
 * @since 3.0.0
 */
const val ATTACHMENT_CLEAR: FetchMode = ATTACHMENT_BIT.inv()

/**
 * All values need to be fetched.
 * @since 3.0.0
 */
const val FETCH_ALL: FetchMode = 15

/**
 * All values are valid now.
 * @since 3.0.0
 */
const val IS_COMPLETE: FetchMode = 15

/**
 * A bit that can be set, to avoid fetching from cache, can be added to [FETCH_ALL] to ensure that all data is fetched from the storage, overriding the currently cached version.
 * @since 3.0.0
 */
const val NO_CACHE_BIT: FetchMode = 16

/**
 * The bitmask to AND apply to clear the no-cache bit.
 * @since 3.0.0
 */
const val NO_CACHE_CLEAR: FetchMode = NO_CACHE_BIT.inv()

/**
 * A mask to clear invalid bits form the fetch-mode.
 * @since 3.0.0
 */
const val FETCH_MASK: FetchMode = 31

