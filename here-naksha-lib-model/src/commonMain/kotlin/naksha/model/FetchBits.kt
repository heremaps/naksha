@file:Suppress("NOTHING_TO_INLINE")

package naksha.model

import naksha.model.FetchMode.FetchMode_C.FETCH_ALL
import naksha.model.FetchMode.FetchMode_C.FETCH_ATTACHMENT
import naksha.model.FetchMode.FetchMode_C.FETCH_FEATURE
import naksha.model.FetchMode.FetchMode_C.FETCH_GEOMETRY
import naksha.model.FetchMode.FetchMode_C.FETCH_ID
import naksha.model.FetchMode.FetchMode_C.FETCH_META
import naksha.model.FetchMode.FetchMode_C.FETCH_NO_CACHE
import naksha.model.FetchMode.FetchMode_C.FETCH_REFERENCE_POINT
import naksha.model.FetchMode.FetchMode_C.FETCH_TAGS

/**
 * Helpful alias for the bits that make the fetch-mode.
 */
typealias FetchBits = Int

/**
 * Create a fetch-bit by combining all given fetch modes.
 * @param modes the fetch-modes to combine into one fetch-bits.
 * @return the combined fetch-bits.
 */
inline fun FetchBits(vararg modes: Int): FetchBits {
    var bits = 0
    for (mode in modes) { bits = bits or mode }
    return bits
}

inline fun FetchBits.withId(): Int = this or FETCH_ID
inline fun FetchBits.id(): Boolean = (this and FETCH_ID) == FETCH_ID

inline fun FetchBits.withMeta(): Int = this or FETCH_META
inline fun FetchBits.meta(): Boolean = (this and FETCH_META) == FETCH_META

inline fun FetchBits.withFeature(): Int = this or FETCH_FEATURE
inline fun FetchBits.feature(): Boolean = (this and FETCH_FEATURE) == FETCH_FEATURE

inline fun FetchBits.withGeometry(): Int = this or FETCH_GEOMETRY
inline fun FetchBits.geometry(): Boolean = (this and FETCH_GEOMETRY) == FETCH_GEOMETRY

inline fun FetchBits.withReferencePoint(): Int = this or FETCH_REFERENCE_POINT
inline fun FetchBits.referencePoint(): Boolean = (this and FETCH_REFERENCE_POINT) == FETCH_REFERENCE_POINT

inline fun FetchBits.withTags(): Int = this or FETCH_TAGS
inline fun FetchBits.tags(): Boolean = (this and FETCH_TAGS) == FETCH_TAGS

inline fun FetchBits.withAttachment(): Int = this or FETCH_ATTACHMENT
inline fun FetchBits.attachment(): Boolean = (this and FETCH_ATTACHMENT) == FETCH_ATTACHMENT

inline fun FetchBits.noCache(): Int = this or FETCH_NO_CACHE
inline fun FetchBits.useCache(): Boolean = (this and FETCH_NO_CACHE) == FETCH_NO_CACHE

inline fun FetchBits.isComplete(): Boolean = (this and FETCH_ALL) == FETCH_ALL
