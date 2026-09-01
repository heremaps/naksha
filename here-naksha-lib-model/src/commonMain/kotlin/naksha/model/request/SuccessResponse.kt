@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.geo.SpFeatureCollection
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaFeatureList
import naksha.model.objects.XyzMembers
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

/**
 * Success response, means all operations succeeded, and it's safe to commit the transaction.
 *
 * The recommended way to use it is:
 * ```kotlin
 * if (response is SuccessResponse) {
 *   val features = response.features
 *   // or
 *   val tuples = response.featureTupleList
 * }
 * ```
 * In Java:
 * ```java
 * if (response instanceof SuccessResponse ok) {
 *   var features = ok.getFeatures();
 *   // or
 *   var tuples = ok.getFeatureTupleList();
 * }
 * ```
 *
 * ## Warning
 * It is highly recommended to only use either [features] or [featureTupleList], because they convert results on demand, so constantly switching between the two is very costly!
 * @since 3.0
 */
@JsExport
open class SuccessResponse() : Response() {
    /**
     * Create a response for the given [features][NakshaFeature], copied into [features].
     * @param features the features that form the success response.
     * @since 3.0
     * @see [features]
     */
    @Suppress("LeakingThis")
    @JsName("fromNakshaFeature")
    constructor(vararg features: NakshaFeature) : this() {
        withFeatures(*features)
    }

    /**
     * Create a response for the given [NakshaFeatureList], copied into [features].
     * @param features the features that form the success response.
     * @since 3.0
     * @see [features]
     */
    @Suppress("LeakingThis")
    @JsName("fromNakshaFeatureList")
    constructor(features: List<NakshaFeature?>) : this() {
        withFeatures(features)
    }

    /**
     * Create a response for the given [NakshaFeatureList], assigned to [features] _(not copied)_.
     * @param features the features that form the success response.
     * @param copy if `true`, copies the given feature list; defaults to `false`.
     * @since 3.0
     * @see [features]
     */
    @Suppress("LeakingThis")
    @JsName("ofNakshaFeatureList")
    @JvmOverloads
    constructor(features: NakshaFeatureList, copy: Boolean = false) : this() {
        withFeatures(features, copy)
    }

    /**
     * Create a response for the given [FeatureTupleList], assigned to [featureTupleList] _(not copied)_.
     * @param featureTupleList the [FeatureTupleList] that form the success response.
     * @param copy if `true`, copies the given feature list; defaults to `false`.
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("ofFeatureTupleList")
    @JvmOverloads
    constructor(featureTupleList: FeatureTupleList, copy: Boolean = false) : this() {
        withFeatureTupleList(featureTupleList, copy)
    }

    /**
     * Create a response for the given [TupleNumberList], copied into [featureTupleList].
     *
     * @param tupleNumberList the [TupleNumberList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("ofTupleNumberList")
    constructor(tupleNumberList: TupleNumberList) : this() {
        withTupleNumberList(tupleNumberList)
    }

    /**
     * Create a response for the given [TupleNumberBinaryArray] given as [ByteArray], copied into [featureTupleList].
     *
     * @param tupleNumberByteArray the [TupleNumberBinaryArray] given as raw [ByteArray].
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("fromTupleNumberByteArray")
    constructor(tupleNumberByteArray:  ByteArray) : this() {
        withTupleNumberByteArray(tupleNumberByteArray)
    }

    /**
     * Create a response for the given [TupleNumberBinaryArray], copied into [featureTupleList].
     *
     * @param tupleNumberBinaryArray the [TupleNumberBinaryArray] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("fromTupleNumberBinaryArray")
    constructor(tupleNumberBinaryArray: TupleNumberBinaryArray) : this() {
        withTupleNumberBinary(tupleNumberBinaryArray)
    }

    /**
     * Create a response for the given [TupleList], copy it into [featureTupleList].
     *
     * @param tupleList the [TupleList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("fromTupleList")
    constructor(tupleList: TupleList) : this() {
        withTupleList(tupleList)
    }

    /**
     * The amount of results being in the response.
     *
     * ## Note
     * It is much better to read [length] than `features.size` or `featureTupleList.size`, because this guarantees no side effect, therefore no modification of the underlying platform object.
     * @since 3.0
     */
    override val length: Int
        get() {
            val features = getAs(FEATURES, NakshaFeatureList::class)
            if (features is NakshaFeatureList) return features.size
            val featureTupleList = getAs(FEATURE_TUPLE_LIST, FeatureTupleList::class)
            return featureTupleList?.size ?: 0
        }

    companion object SuccessResponse_C {
        private const val FEATURE_TUPLE_LIST = "featureTupleList"
        private const val FEATURES = "features"
    }

    /**
     * The [feature tuples][FeatureTuple] being part of the response.
     *
     * - Setting the [featureTupleList], automatically clears the [features].
     * - Reading the [featureTupleList], automatically convert set [features] into [FeatureTuple], clearing [features].
     * @since 3.0
     */
    open var featureTupleList: FeatureTupleList
        get() {
            var list = getAs(FEATURE_TUPLE_LIST, FeatureTupleList::class)
            if (list != null) return list
            list = FeatureTupleList()

            // Optionally convert existing features.
            val featureList = getAs(FEATURES, NakshaFeatureList::class)
            if (featureList != null) {
                list.setCapacity(featureList.size)
                for (feature in featureList) {
                    if (feature == null) continue
                    // TODO: We need to fix this, this is a very dirty hack, but we need to expose the tuple handling expilicty.
                    //       So to say, lets remove the whole FeatureTuple context; a request returns a TupleNumberList.
                    //       Then the user needs to turn this explicitly into a TupleList, and this into a FeatureList.
                    //       We can offer a helper method that does all steps at ones, so turns a SuccessResponse into list of features.
                    //       However, we want to expose the TupleNumber design explicitly.
                    list.add(FeatureTuple(feature, XyzMembers.XyzTn))
                }
            }

            setRaw(FEATURE_TUPLE_LIST, list)
            removeRaw(FEATURES)
            return list
        }
        set(value) {
            setRaw(FEATURE_TUPLE_LIST, value)
            removeRaw(FEATURES)
        }

    /**
     * The result converted into a [NakshaFeatureList].
     *
     * Reading [features] can cause network IO, because when the local cache does not hold the [Tuple], it need to load them from the storage, or any remote cache!
     *
     * - Setting the [features], automatically clears the [featureTupleList].
     * - Reading the [features], automatically convert set [featureTupleList] into [NakshaFeatureList], clearing [featureTupleList].
     * @since 3.0
     */
    open var features: NakshaFeatureList
        get() {
            var list = getAs(FEATURES, NakshaFeatureList::class)
            if (list != null) return list
            list = NakshaFeatureList()

            // Optionally convert existing feature-tuple.
            val featureTupleList = getAs(FEATURE_TUPLE_LIST, FeatureTupleList::class)
            if (featureTupleList != null) {
                featureTupleList.loadAll(acceptFeature = true)
                list.setCapacity(featureTupleList.size)
                for (tuple in featureTupleList) {
                    if (tuple == null) continue
                    list.add(tuple.feature) // TODO (Jakub): tc0280 - adds null
                }
            }

            setRaw(FEATURES, list)
            removeRaw(FEATURE_TUPLE_LIST)
            return list
        }
        set(value) {
            setRaw(FEATURES, value)
            removeRaw(FEATURE_TUPLE_LIST)
        }

    /**
     * Copy given [features][NakshaFeature] into [features].
     * @param features the features that form the success response.
     * @return this.
     * @since 3.0
     * @see [features]
     */
    open fun withFeatures(vararg features: NakshaFeature): SuccessResponse {
        val list = NakshaFeatureList()
        list.setCapacity(features.size)
        list.addAll(features)
        this.features = list
        return this
    }

    /**
     * Copy the given [features][NakshaFeature] into [features].
     * @param features the features that form the success response.
     * @return this.
     * @since 3.0
     * @see [features]
     */
    @JsName("withFeatureList")
    open fun withFeatures(features: List<NakshaFeature?>): SuccessResponse {
        val list = NakshaFeatureList()
        list.setCapacity(features.size)
        list.addAll(features)
        this.features = list
        return this
    }

    /**
     * Assign or copy the given [NakshaFeatureList] to [features].
     * @param features the features that form the success response.
     * @param copy if `true`, copies the given feature list; defaults to `false`.
     * @return this.
     * @since 3.0
     * @see [features]
     */
    @JsName("withNakshaFeatureList")
    @JvmOverloads
    open fun withFeatures(features: NakshaFeatureList, copy: Boolean = false): SuccessResponse {
        val list: NakshaFeatureList
        if (copy) {
            list = NakshaFeatureList()
            list.setCapacity(features.size)
            list.addAll(features)
        } else {
            list = features
        }
        this.features = list
        return this
    }

    /**
     * Copy the given [FeatureTuple] into [featureTupleList].
     * @param list the list of [FeatureTuple] that form the success response.
     * @since 3.0
     * @see [list]
     */
    @JsName("withFeatureTuple")
    open fun withFeatureTupleList(list: List<FeatureTuple?>): SuccessResponse {
        val featureTupleList = FeatureTupleList()
        featureTupleList.setCapacity(list.size)
        featureTupleList.addAll(list)
        this.featureTupleList = featureTupleList
        return this
    }

    /**
     * Create a response for the given [FeatureTupleList], assigned to [featureTupleList] _(not copied)_.
     * @param featureTupleList the [FeatureTupleList] that form the success response.
     * @param copy if `true`, copies the given feature list; defaults to `false`.
     * @since 3.0
     * @see [featureTupleList]
     */
    @JvmOverloads
    open fun withFeatureTupleList(featureTupleList: FeatureTupleList, copy: Boolean = false): SuccessResponse {
        val list: FeatureTupleList
        if (copy) {
            list = FeatureTupleList()
            list.setCapacity(featureTupleList.size)
            list.addAll(featureTupleList)
        } else {
            list = featureTupleList
        }
        this.featureTupleList = list
        return this
    }

    /**
     * Create a response for the given [TupleNumberList], copied into [featureTupleList].
     *
     * @param tupleNumberList the [TupleNumberList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun withTupleNumberList(tupleNumberList: TupleNumberList): SuccessResponse {
        val list = FeatureTupleList()
        list.setCapacity(tupleNumberList.size)
        for (tupleNumber in tupleNumberList) {
            if (tupleNumber == null) continue
            list.add(FeatureTuple(tupleNumber))
        }
        this.featureTupleList = list
        return this
    }

    /**
     * Sets the [featureTupleList] to the decoded [tuple-number's][naksha.base.TupleNumber] read from the [TupleNumberBinaryArray], encoded in the given [ByteArray]. Basically, this will automatically wrap the given [ByteArray] into an [TupleNumberBinaryArray], and then convert it into a [FeatureTupleList].
     *
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun withTupleNumberByteArray(value: ByteArray): SuccessResponse {
        withTupleNumberBinary(TupleNumberBinaryArray(value))
        return this
    }

    /**
     * Sets the [featureTupleList] to the decoded [tuple-number's][naksha.base.TupleNumber] read from the given [TupleNumberBinaryArray].
     *
     * This constructor will convert the binary-array into a [FeatureTupleList].
     *
     * @since 3.0
     * @see [featureTupleList]
     */
    // TODO: CASL-942 filter properties
    open fun withTupleNumberBinary(value: TupleNumberBinaryArray): SuccessResponse {
        val list = FeatureTupleList()
        list.setCapacity(value.size)
        for (tupleNumber in value) {
            if (tupleNumber != null) {
                list.add(FeatureTuple(tupleNumber, null))
            }
        }
        featureTupleList = list
        return this
    }

    /**
     * Create a response for the given [TupleList], copy it into [featureTupleList].
     *
     * @param tupleList the [TupleList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun withTupleList(tupleList: TupleList): SuccessResponse {
        val list = FeatureTupleList()
        list.setCapacity(tupleList.size)
        for (tuple in tupleList) {
            if (tuple == null) continue
            list.add(FeatureTuple(tuple.tupleNumber, tuple))
        }
        featureTupleList = list
        return this
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
     * import static naksha.base.Platform.javaProxy;
     * final SpFeatureCollection collection = ...;
     * final SuccessResponse response =
     *       javaProxy(collection, SuccessResponse.class);
     * ```
     * @return this response as [GeoJSON feature collection][SpFeatureCollection].
     * @since 3.0
     */
    fun asFeatureCollection(): SpFeatureCollection {
        this.features
        return proxy(SpFeatureCollection::class)
    }

    /**
     * Applies a sequence of filters to the feature tuples in this response.
     *
     * @param filters The result filters to apply, passed as variable arguments.
     * @return This `SuccessResponse` instance.
     * @since 3.0
     */
    fun filterResults(vararg filters: ResultFilter): SuccessResponse {
        if (filters.isEmpty()) {
            return this
        }

        val tupleList = this.featureTupleList
        if (tupleList.isEmpty()) {
            return this
        }

        tupleList.loadAll(acceptFeature = true)

        var currentList = tupleList.asList().filterNotNull()
        for (filter in filters.filterNotNull()) {
            currentList = currentList.mapNotNull { featureTuple -> filter.filter(featureTuple) }
        }

        this.withFeatureTupleList(currentList)
        return this
    }

    @Deprecated(message = "Use features property", replaceWith = ReplaceWith("features"), level = DeprecationLevel.ERROR)
    fun useFeatures(): NakshaFeatureList = features

    @Deprecated(message = "Use featureTupleList property", replaceWith = ReplaceWith("featureTupleList"), level = DeprecationLevel.ERROR)
    fun useFeatureTupleList(): FeatureTupleList = featureTupleList
}