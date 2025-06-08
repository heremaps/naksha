@file:Suppress("NOTHING_TO_INLINE", "unused")

// This will be exposed
// - in JavaScript at the namespace: naksha.model.{name}
// - jn Java at the class naksha.model.FetchModeKt.{name}
package naksha.model

/**
 * Helpful alias for the bits that signal with parts of a [Tuple] a client needs to be fetched.
 * @since 3.0.0
 */
typealias FetchMode = Int

/**
 * Create a fetch-mode by combining all given fetch-bits.
 * @param modes the fetch-bits to combine into one fetch-mode.
 * @return the combined fetch-bits.
 * @since 3.0.0
 */
inline fun FetchMode(vararg modes: Int): FetchMode {
    var bits = 0
    for (mode in modes) { bits = bits or mode }
    return bits
}

/**
 * Set the [meta][Tuple.meta] bit.
 * @since 3.0.0
 */
inline fun FetchMode.withMeta(): Int = this or META_BIT

/**
 * Clear the [meta][Tuple.meta] bit.
 * @since 3.0.0
 */
inline fun FetchMode.noMeta(): Int = this and META_CLEAR

/**
 * Test if the [meta][Tuple.meta] bit is set.
 * @since 3.0.0
 */
inline fun FetchMode.fetchMeta(): Boolean = (this and META_BIT) == META_BIT

/**
 * Set the _feature_ bit (covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchMode.withFeature(): Int = this or FEATURE_BIT

/**
 * Clear the _feature_ bit (covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchMode.noFeature(): Int = this and FEATURE_CLEAR

/**
 * Test if the _feature_ bit is set (which covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchMode.fetchFeature(): Boolean = (this and FEATURE_BIT) == FEATURE_BIT

/**
 * Set the _geometry_ bit (covers [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchMode.withGeometry(): Int = this or GEOMETRY_BIT

/**
 * Clear the _geometry_ bit ([geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchMode.noGeometry(): Int = this and GEOMETRY_CLEAR

/**
 * Test if the _geometry_ bit is set (covers [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchMode.fetchGeometry(): Boolean = (this and GEOMETRY_BIT) == GEOMETRY_BIT

/**
 * Set the [attachment][Tuple.attachment] bit.
 * @since 3.0.0
 */
inline fun FetchMode.withAttachment(): Int = this or ATTACHMENT_BIT

/**
 * Clear the [attachment][Tuple.attachment] bit.
 * @since 3.0.0
 */
inline fun FetchMode.noAttachment(): Int = this and ATTACHMENT_CLEAR

/**
 * Test if the [attachment][Tuple.attachment] bit is set.
 * @since 3.0.0
 */
inline fun FetchMode.fetchAttachment(): Boolean = (this and ATTACHMENT_BIT) == ATTACHMENT_BIT

/**
 * Allow reading from cache.
 * @since 3.0.0
 */
inline fun FetchMode.withCache(): Int = this or NO_CACHE_BIT

/**
 * Do not read cache, always query the storage.
 * @since 3.0.0
 */
inline fun FetchMode.noCache(): Int = this and NO_CACHE_CLEAR

/**
 * Are we okay in fetching the requested parts from cache?
 * @since 3.0.0
 */
inline fun FetchMode.fetchCache(): Boolean = (this and NO_CACHE_BIT) == NO_CACHE_BIT

/**
 * Tests if all parts are to be fetched.
 * @since 3.0.0
 */
inline fun FetchMode.fetchAll(): Boolean = (this and FETCH_ALL) == FETCH_ALL