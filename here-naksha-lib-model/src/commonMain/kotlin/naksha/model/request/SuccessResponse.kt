@file:Suppress("OPT_IN_USAGE", "DEPRECATION")

package naksha.model.request

import naksha.geo.SpFeatureCollection
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaFeatureList
import kotlin.js.JsExport
import kotlin.js.JsName

// TODO: Link an external (GitHub) Markdown file (? LIFECYCLE.md ?), that in details explains:
//       What is a feature? (a map object)
//       What is a tuple? (a specific immutable, globally unique, state of a feature)
//       What is a tuple-number (a binary 228-bit value referring to a tuple)
//       What representations there are
//       - byte-array encoding: ByteArray, TupleNumberBinaryArray, TupleBinary, TupleBinaryArray
//       - heap-representations: TupleNumber, TupleNumberList, Tuple, FeatureTuple, NakshaFeature

/**
 * Success response, means all operations succeeded, and it's safe to commit the transaction.
 *
 * The recommended way to use it is:
 * ```kotlin
 * if (response is SuccessResponse) {
 *   val features = response.useFeaturesOnly()
 * }
 * ```
 * The usage of [useFeaturesOnly] ensures that all not needed data is removed from the result-set and only the needed [features] stay in it. This simplifies result serialization as [naksha.geo.SpFeatureCollection].
 *
 * ### Note
 * Generally, a result-set can have different representations of the result-set. Some are very low-level, with others being very high-level ones (like basically GeoJSON's). The order in which they should be treated is:
 * - [features]
 * - [featureTupleList]
 * - [tupleNumberList]
 * - [tupleNumberBinary]
 *
 * **It is highly recommended, unless there is some specific demand, to only use the [features].**
 * @since 3.0
 */
@JsExport
open class SuccessResponse() : Response() {
    /**
     * Create a response for the given features.
     * @param features the features that form the success response.
     * @since 3.0
     */
    @JsName("ofNakshaFeature")
    constructor(vararg features: NakshaFeature?) : this() {
        val list = NakshaFeatureList()
        list.setCapacity(features.size)
        list.addAll(features)
        setRaw(FEATURES, list)
    }

    /**
     * Create a response for the given features.
     * @param features the features that form the success response.
     * @since 3.0
     */
    @JsName("ofList")
    constructor(features: List<NakshaFeature?>?) : this() {
        if (features != null) {
            val list = NakshaFeatureList()
            list.setCapacity(features.size)
            list.addAll(features)
            setRaw(FEATURES, list)
        }
    }

    /**
     * Create a response for the given features.
     * @param features the features that form the success response.
     * @since 3.0
     */
    @JsName("ofNakshaFeatureList")
    constructor(features: NakshaFeatureList?) : this() {
        if (features != null) {
            setRaw(FEATURES, features)
        }
    }

    /**
     * Create a response for the given [FeatureTupleList].
     * @param featureTuples the [FeatureTupleList] that form the success response.
     * @since 3.0
     */
    @JsName("ofFeatureTupleList")
    constructor(featureTuples: FeatureTupleList?) : this() {
        if (featureTuples != null) {
            setRaw(FEATURE_TUPLE_LIST, featureTuples)
        }
    }

    /**
     * Create a response for the given [TupleNumberList].
     * @param tupleNumberList the [TupleNumberList] that form the success response.
     * @since 3.0
     */
    @JsName("ofTupleNumberList")
    constructor(tupleNumberList: TupleNumberList?) : this() {
        if (tupleNumberList != null) {
            setRaw(TUPLE_NUMBER_LIST, tupleNumberList)
        }
    }

    /**
     * Create a response for the given [TupleNumberBinaryArray].
     * @param tupleNumberBinaryArray the [TupleNumberBinaryArray] that form the success response.
     * @since 3.0
     */
    @JsName("ofTupleNumberBinaryArray")
    constructor(tupleNumberBinaryArray: TupleNumberBinaryArray?) : this() {
        if (tupleNumberBinaryArray != null) {
            setRaw(TUPLE_NUMBER_BINARY, tupleNumberBinaryArray)
        }
    }

    /**
     * Create a response for the given [TupleList], converting it into a [FeatureTupleList].
     * @param tupleList the [TupleList] that form the success response.
     * @since 3.0
     */
    @JsName("ofTupleList")
    constructor(tupleList: TupleList?) : this() {
        if (tupleList != null) {
            val list = FeatureTupleList()
            list.setCapacity(tupleList.size)
            for (tuple in tupleList) {
                if (tuple == null) continue
                list.add(FeatureTuple(tuple.tupleNumber, tuple))
            }
            setRaw(FEATURE_TUPLE_LIST, list)
        }
    }

    companion object SuccessResponse_C {
        private const val TUPLE_NUMBER_BINARY = "tupleNumberBinary"
        private const val TUPLE_NUMBER_LIST = "tupleNumberList"
        private const val FEATURE_TUPLE_LIST = "featureTupleList"
        private const val FEATURES = "features"
    }

    /**
     * Returns the size of the result.
     *
     * ### Note
     * As there are different representations, this method tests the different representations in the following order:
     * - [features] - does not cause feature creation, if not yet done already!
     * - [featureTupleList]
     * - [tupleNumberList]
     * - [tupleNumberBinary]
     *
     * Therefore, when a client modifies the [features], this changes the result-size, even while the underlying, lower-level, results may still be larger.
     * @since 3.0
     */
    override fun resultSize(): Int {
        val raw = getAs(FEATURES, NakshaFeatureList::class)
        if (raw is NakshaFeatureList) return raw.size
        return featureTupleList?.size ?: tupleNumberList?.size ?: tupleNumberBinary?.size ?: 0
    }

    /**
     * The binary [tuple-number][naksha.model.TupleNumber] representation, which basically is a [TupleNumberBinaryArray], as returned by the storage. The raw value can be as well an [ByteArray], in which case it will automatically be wrapper into an [TupleNumberBinaryArray].
     *
     * This binary can be serialized technically, but just using the [Platform.toJSON][naksha.base.Platform.toJSON] method, because it requires a binary encoding, which means serialization and deserialization into a [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data), which is normally not supported out of the box by standard JSON parsers/serializers, it is a proprietary extension to the JSON standard, the same way that 64-bit integers are handled as special [Data URL](https://developer.mozilla.org/en-US/docs/Web/URI/Reference/Schemes/data) by [Platform.toJSON][naksha.base.Platform.toJSON].
     * @since 3.0
     */
    open var tupleNumberBinary: TupleNumberBinaryArray?
        get() {
            val raw = get(TUPLE_NUMBER_BINARY)
            if (raw is TupleNumberBinaryArray) return raw
            if (raw is ByteArray) {
                val value = TupleNumberBinaryArray(raw)
                set(TUPLE_NUMBER_BINARY, value)
                return value
            }
            return null
        }
        set(value) {
            if (value == null) removeRaw(TUPLE_NUMBER_BINARY) else set(TUPLE_NUMBER_BINARY, value)
        }

    /**
     * @see [tupleNumberBinary]
     */
    fun withTupleNumberBinary(value: TupleNumberBinaryArray?): SuccessResponse {
        tupleNumberBinary = value
        return this
    }

    /**
     * @see [tupleNumberBinary]
     */
    @JsName("withTupleNumberByteArray")
    fun withTupleNumberBinary(value: ByteArray?): SuccessResponse {
        tupleNumberBinary = if (value == null) null else TupleNumberBinaryArray(value)
        return this
    }

    /**
     * Returns this response without the [tupleNumberBinary].
     * @return this.
     * @since 3.0
     */
    fun withoutTupleNumberBinary(): SuccessResponse {
        removeRaw(TUPLE_NUMBER_BINARY)
        return this
    }

    /**
     * The [tuple-number][naksha.model.TupleNumber] representation; if any is available.
     * @since 3.0
     */
    open var tupleNumberList: TupleNumberList?
        get() = getAs(TUPLE_NUMBER_LIST, TupleNumberList::class)
        set(value) {
            if (value == null) removeRaw(TUPLE_NUMBER_LIST) else set(TUPLE_NUMBER_LIST, value)
        }

    /**
     * @see [tupleNumberList]
     */
    fun withTupleNumberList(value: TupleNumberList?): SuccessResponse {
        tupleNumberList = value
        return this
    }

    /**
     * Returns this response without the [tupleNumberList].
     * @return this.
     * @since 3.0
     */
    fun withoutTupleNumberList(): SuccessResponse {
        removeRaw(TUPLE_NUMBER_LIST)
        return this
    }

    /**
     * The [feature tuples][FeatureTuple] being part of the response.
     */
    open var featureTupleList: FeatureTupleList?
        get() = getAs(FEATURE_TUPLE_LIST, FeatureTupleList::class)
        set(value) {
            if (value == null) removeRaw(FEATURE_TUPLE_LIST) else setRaw(FEATURE_TUPLE_LIST, value)
        }

    /**
     * Returns the result as [FeatureTupleList].
     *
     * If none available, generate a [FeatureTupleList] from [featureTupleList], [tupleNumberList], or [tupleNumberBinary], in this order.
     * @return the results as [FeatureTupleList].
     * @since 3.0
     */
    fun useFeatureTupleList(): FeatureTupleList {
        var featureTupleList: FeatureTupleList? = this.featureTupleList
        if (featureTupleList != null) {
            return featureTupleList
        } else {
            val list = this.tupleNumberList
            if (list != null) {
                featureTupleList = FeatureTupleList()
                featureTupleList.setCapacity(list.size)
                for (tupleNumber in list) {
                    if (tupleNumber is TupleNumber) {
                        featureTupleList.add(FeatureTuple(tupleNumber))
                    }
                }
            }
        }

        if (featureTupleList == null) {
            val list = this.tupleNumberBinary
            if (list != null) {
                featureTupleList = FeatureTupleList()
                featureTupleList.setCapacity(list.size)
                for (tupleNumber in list) {
                    if (tupleNumber is TupleNumber) {
                        featureTupleList.add(FeatureTuple(tupleNumber))
                    }
                }
            }
        }

        if (featureTupleList == null) {
            featureTupleList = FeatureTupleList()
            val features = getAs(FEATURES, NakshaFeatureList::class)
            if (features is NakshaFeatureList) {
                for (feature in features) {
                    if (feature == null) continue
                    val tn = feature.properties.xyz.guid?.tupleNumber ?: continue
                    val featureTuple = FeatureTuple(tn)
                    featureTuple.feature = feature
                    featureTupleList.add(featureTuple)
                }
            }
        }
        this.featureTupleList = featureTupleList
        return featureTupleList
    }

    /**
     * @see [featureTupleList]
     */
    @JsName("withListOfFeatureTuple")
    fun withFeatureTupleList(value: List<FeatureTuple?>?): SuccessResponse {
        if (value is FeatureTupleList) {
            featureTupleList = value
        } else if (value != null) {
            val list = FeatureTupleList()
            list.setCapacity(value.size)
            list.addAll(value)
            featureTupleList = list
        } else {
            featureTupleList = null
        }
        return this
    }

    /**
     * @see [featureTupleList]
     */
    fun withFeatureTupleList(value: FeatureTupleList?): SuccessResponse {
        featureTupleList = value
        return this
    }

    /**
     * Returns this response without the [featureTupleList].
     * @return this.
     * @since 3.0
     */
    fun withoutFeatureTupleList(): SuccessResponse {
        removeRaw(FEATURE_TUPLE_LIST)
        return this
    }

    /**
     * The result converted into a list of [NakshaFeature][naksha.model.objects.NakshaFeature].
     *
     * ## Warning
     * If you do not need the results, but only the [TupleNumber] or the amount of operations that were performed, do **NOT** read `features.size` _(in Java `getFeatures()`)_, rather query [resultSize] and/or [useFeatureTupleList], because these methods are not going to read from storage, they are instant with zero network IO.
     *
     * Reading [features] can cause network IO, because when the local cache does not hold the [Tuple], it need to load them from the storage, or any remote cache!
     * @since 3.0
     */
    open var features: NakshaFeatureList
        get() {
            val raw = getAs(FEATURES, NakshaFeatureList::class)
            if (raw is NakshaFeatureList) return raw

            val features = NakshaFeatureList()
            val featureTupleList = useFeatureTupleList()
            features.setCapacity(featureTupleList.size)
            Naksha.cache.load(featureTupleList)
            for (featureTuple in featureTupleList) {
                val feature = featureTuple?.feature
                if (feature != null) features.add(feature)
            }
            setRaw(FEATURES, features)
            return features
        }
        set(value) {
            @Suppress("SENSELESS_COMPARISON")
            if (value == null) removeRaw(FEATURES) else setRaw(FEATURES, value)
        }

    /**
     * @see [features]
     */
    fun withFeatures(value: List<NakshaFeature?>?): SuccessResponse {
        if (value == null) {
            removeRaw(FEATURES)
            return this
        }
        features = if (value is NakshaFeatureList) value else {
            val list = NakshaFeatureList()
            list.addAll(value)
            list
        }
        return this
    }

    /**
     * Returns this response without the [features].
     * @return this.
     * @since 3.0
     */
    fun withoutFeatures(): SuccessResponse {
        removeRaw(FEATURES)
        return this
    }

    /**
     * Calls [useFeatures] to ensure that the [features] are generated, then removes all other alternative representations, and returns this.
     *
     * So this method ensures that [features] are available, and removes:
     * - [tupleNumberBinary]
     * - [tupleNumberList]
     * - [featureTupleList]
     *
     * @return this.
     * @since 3.0
     */
    fun withFeaturesOnly(): SuccessResponse {
        useFeatures()
        removeRaw(TUPLE_NUMBER_BINARY)
        removeRaw(TUPLE_NUMBER_LIST)
        removeRaw(FEATURE_TUPLE_LIST)
        return this
    }

    /**
     * Returns or generates a [NakshaFeatureList] from the other possible representations.
     * @see [features]
     */
    fun useFeatures(): NakshaFeatureList = features

    /**
     * Calls [useFeatures] to ensure that the [features] are generated, then removes all other alternative representations, and returns the [features]. This allows to [proxy] this response into a [GeoJSON collection][SpFeatureCollection] via [asFeatureCollection].
     *
     * So this method ensures that [features] are available, and removes:
     * - [tupleNumberBinary]
     * - [tupleNumberList]
     * - [tupleList]
     * - [featureTupleList]
     *
     * @return the [features].
     * @since 3.0
     */
    fun useFeaturesOnly(): NakshaFeatureList {
        val features = useFeatures()
        removeRaw(TUPLE_NUMBER_BINARY)
        removeRaw(TUPLE_NUMBER_LIST)
        removeRaw(FEATURE_TUPLE_LIST)
        return features
    }

    /**
     * Returns this success-response as [GeoJSON feature collection][SpFeatureCollection].
     *
     * To turn a [GeoJSON feature collection][SpFeatureCollection] into a [SuccessResponse], just do:
     * ```kotlin
     * val collection: SpFeatureCollection = ...;
     * val response = collection.proxy(SuccessResponse::class)
     * ```
     * Or in Java:
     * ```java
     * import static naksha.base.Platform.klassFor;
     * final SpFeatureCollection collection = ...;
     * final SuccessResponse response =
     *       collection.proxy(klassFor(SuccessResponse.class));
     * ```
     *
     * ### Note
     * Before calling this method, it is recommended to invoke [useFeaturesOnly].
     *
     * @return this response as [GeoJSON feature collection][SpFeatureCollection].
     * @since 3.0
     */
    fun asFeatureCollection(): SpFeatureCollection {
        useFeaturesOnly()
        // TODO: We need to ensure that the "type" property is set, and optionally create the bounding box!
        return proxy(SpFeatureCollection::class)
    }
}