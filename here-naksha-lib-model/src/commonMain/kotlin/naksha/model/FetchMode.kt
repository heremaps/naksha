@file:Suppress("NOTHING_TO_INLINE")

package naksha.model

/**
 * Helpful alias for the bits that make the fetch-mode.
 */
typealias FetchMode = Int

/**
 * Fetch the [meta][Tuple.meta].
 */
const val FETCH_META: FetchMode = 1

/**
 * The bitmask to AND apply to clear the meta bit.
 */
const val FETCH_META_CLEAR: FetchMode = FETCH_META.inv()

/**
 * Fetch the [geometry][Tuple.geo], and the [reference-point][Tuple.referencePoint].
 */
const val FETCH_GEOMETRY: FetchMode = 2

/**
 * The bitmask to AND apply to clear the geometry bit.
 */
const val FETCH_GEOMETRY_CLEAR: FetchMode = FETCH_GEOMETRY.inv()

/**
 * Fetch the [feature][Tuple.feature], and [tags][Tuple.tags].
 */
const val FETCH_FEATURE: FetchMode = 4

/**
 * The bitmask to AND apply to clear the feature bit.
 */
const val FETCH_FEATURE_CLEAR: FetchMode = FETCH_FEATURE.inv()

/**
 * Fetch the [attachment][Tuple.attachment].
 */
const val FETCH_ATTACHMENT: FetchMode = 8

/**
 * The bitmask to AND apply to clear the attachment bit.
 */
const val FETCH_ATTACHMENT_CLEAR: FetchMode = FETCH_ATTACHMENT.inv()

/**
 * Fetch the full [Tuple].
 */
const val FETCH_ALL: FetchMode = 15

/**
 * A bit that can be set, to avoid fetching from cache, can be added to [FETCH_ALL] to ensure that all data is fetched from the storage, overriding the currently cached version.
 */
const val FETCH_NO_CACHE: FetchMode = 16

/**
 * The bitmask to AND apply to clear the no-cache bit.
 */
const val FETCH_NO_CACHE_CLEAR: FetchMode = FETCH_NO_CACHE.inv()

/**
 * A mask to clear invalid bits form the fetch-mode.
 */
const val FETCH_MASK: FetchMode = 31

/**
 * Create a fetch-bit by combining all given fetch modes.
 * @param modes the fetch-modes to combine into one fetch-bits.
 * @return the combined fetch-bits.
 */
inline fun FetchMode(vararg modes: Int): FetchMode {
    var bits = 0
    for (mode in modes) { bits = bits or mode }
    return bits
}

/**
 * Fetch [meta][Tuple.meta].
 */
inline fun FetchMode.withMeta(): Int = this or FETCH_META

/**
 * Do not fetch [meta][Tuple.meta].
 */
inline fun FetchMode.noMeta(): Int = this and FETCH_META_CLEAR

/**
 * Do fetch [meta][Tuple.meta]?
 */
inline fun FetchMode.fetchMeta(): Boolean = (this and FETCH_META) == FETCH_META



/**
 * Fetch [feature][Tuple.feature] and [tags][Tuple.tags].
 */
inline fun FetchMode.withFeature(): Int = this or FETCH_FEATURE

/**
 * Do not fetch [feature][Tuple.feature] and [tags][Tuple.tags].
 */
inline fun FetchMode.noFeature(): Int = this and FETCH_FEATURE_CLEAR

/**
 * Are [feature][Tuple.feature] and [tags][Tuple.tags] to be fetched?
 */
inline fun FetchMode.fetchFeature(): Boolean = (this and FETCH_FEATURE) == FETCH_FEATURE



/**
 * Fetch [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint].
 */
inline fun FetchMode.withGeometry(): Int = this or FETCH_GEOMETRY

/**
 * Do not fetch [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint].
 */
inline fun FetchMode.noGeometry(): Int = this and FETCH_GEOMETRY_CLEAR

/**
 * Are [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint] to be fetched?
 */
inline fun FetchMode.fetchGeometry(): Boolean = (this and FETCH_GEOMETRY) == FETCH_GEOMETRY



/**
 * Fetch [attachment][Tuple.attachment].
 */
inline fun FetchMode.withAttachment(): Int = this or FETCH_ATTACHMENT

/**
 * Do not fetch [attachment][Tuple.attachment].
 */
inline fun FetchMode.noAttachment(): Int = this and FETCH_ATTACHMENT_CLEAR

/**
 * Do fetch [attachment][Tuple.attachment]?
 */
inline fun FetchMode.fetchAttachment(): Boolean = (this and FETCH_ATTACHMENT) == FETCH_ATTACHMENT



/**
 * Allow reading from cache.
 */
inline fun FetchMode.withCache(): Int = this or FETCH_NO_CACHE

/**
 * Do not read cache, always query the storage.
 */
inline fun FetchMode.noCache(): Int = this and FETCH_NO_CACHE_CLEAR

/**
 * Are we okay in fetching the requested parts from cache?
 */
inline fun FetchMode.fetchCache(): Boolean = (this and FETCH_NO_CACHE) == FETCH_NO_CACHE

/**
 * Tests if all parts are fetched.
 */
inline fun FetchMode.isComplete(): Boolean = (this and FETCH_ALL) == FETCH_ALL
