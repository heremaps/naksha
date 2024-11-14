@file:Suppress("NOTHING_TO_INLINE", "unused")
package naksha.model

/**
 * Helpful alias for the bits that signal which parts of a [Tuple] have been fetched already.
 * @since 3.0.0
 */
typealias FetchState = Int

/**
 * Set the [meta][Tuple.meta] bit.
 * @since 3.0.0
 */
inline fun FetchState.setMeta(): Int = this or META_BIT

/**
 * Clear the [meta][Tuple.meta] bit.
 * @since 3.0.0
 */
inline fun FetchState.clearMeta(): Int = this and META_CLEAR

/**
 * Test if the [meta][Tuple.meta] bit is set.
 * @since 3.0.0
 */
inline fun FetchState.hasMeta(): Boolean = (this and META_BIT) == META_BIT

/**
 * Set the _feature_ bit (covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchState.setFeature(): Int = this or FEATURE_BIT

/**
 * Clear the _feature_ bit (covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchState.clearFeature(): Int = this and FEATURE_CLEAR

/**
 * Test if the _feature_ bit is set (which covers [feature][Tuple.feature] and [tags][Tuple.tags]).
 * @since 3.0.0
 */
inline fun FetchState.hasFeature(): Boolean = (this and FEATURE_BIT) == FEATURE_BIT

/**
 * Set the _geometry_ bit (covers [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchState.setGeometry(): Int = this or GEOMETRY_BIT

/**
 * Clear the _geometry_ bit ([geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchState.clearGeometry(): Int = this and GEOMETRY_CLEAR

/**
 * Test if the _geometry_ bit is set (covers [geometry][Tuple.geo] and [reference-point][Tuple.referencePoint]).
 * @since 3.0.0
 */
inline fun FetchState.hasGeometry(): Boolean = (this and GEOMETRY_BIT) == GEOMETRY_BIT

/**
 * Set the [attachment][Tuple.attachment] bit.
 * @since 3.0.0
 */
inline fun FetchState.setAttachment(): Int = this or ATTACHMENT_BIT

/**
 * Clear the [attachment][Tuple.attachment] bit.
 * @since 3.0.0
 */
inline fun FetchState.clearAttachment(): Int = this and ATTACHMENT_CLEAR

/**
 * Test if the [attachment][Tuple.attachment] bit is set.
 * @since 3.0.0
 */
inline fun FetchState.hasAttachment(): Boolean = (this and ATTACHMENT_BIT) == ATTACHMENT_BIT

/**
 * Tests if all parts are loaded.
 * @since 3.0.0
 */
inline fun FetchState.isComplete(): Boolean = (this and IS_COMPLETE) == IS_COMPLETE