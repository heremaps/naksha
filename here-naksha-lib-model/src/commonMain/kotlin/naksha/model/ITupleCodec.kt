@file:OptIn(ExperimentalJsExport::class)

package naksha.model

import naksha.jbon.IDictManager
import naksha.model.objects.NakshaFeature
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * An interface of a tuple-codec; this is code that allows to encode and decode a [Tuple].
 *
 * When [Tuple] are cached somewhere, together with them, all used dictionaries for these [Tuple] need to be cached, to be able to decode them. Therefore, the cache should somehow provide a way to query for the [ITupleCodec], which should provide a [dictionary manager][IDictManager], that can return all dictionaries being needed to decode the tuples.
 * @since 3.0.0
 */
@JsExport
interface ITupleCodec {
    /**
     * Returns the dictionary manager.
     * @return the dictionary manager of this tuple-codec.
     * @since 3.0.0
     */
    fun dictManager(): IDictManager

    /**
     * Convert the given [Tuple] into a [NakshaFeature].
     * @param tuple the tuple to convert.
     * @return the [NakshaFeature] generated from the tuple.
     * @since 3.0.0
     */
    fun tupleToFeature(tuple: Tuple): NakshaFeature

    /**
     * Convert the given [NakshaFeature] into a [Tuple].
     * @param feature the feature to convert.
     * @return the [Tuple] generated from the given feature.
     * @since 3.0.0
     */
    fun featureToTuple(feature: NakshaFeature): Tuple
}