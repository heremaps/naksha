@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformList
import naksha.base.PlatformListApi.PlatformListApi_C.list_get_length
import naksha.base.PlatformType
import naksha.geo.GeoFeature
import naksha.geo.GeoFeatureList
import naksha.model.*
import naksha.model.objects.NakshaFeature
import naksha.model.objects.NakshaFeatureList
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
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
 * It is highly recommended to only use either [getFeatures] or [featureTupleList], because they convert results on demand, so constantly switching between the two is very costly!
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
    @JsName("SuccessResponseOf")
    constructor(vararg features: NakshaFeature) : this() {
        val list = NakshaFeatureList()
        list.setCapacity(features.size)
        for (feature in features) list.add(feature)
        setFeatures(list)
    }

    /**
     * Create a response for the given [NakshaFeatureList], copied into [features].
     * @param features the features that form the success response.
     * @since 3.0
     * @see [features]
     */
    @Suppress("LeakingThis")
    @JsName("SuccessResponseOfList")
    constructor(features: List<NakshaFeature?>) : this() {
        setFeatures(features)
    }

    /**
     * Create a response for the given [FeatureTupleList], assigned to [featureTupleList] _(not copied)_.
     * @param featureTupleList the [FeatureTupleList] that form the success response.
     * @param copy if `true`, copies the given feature list; defaults to `false`.
     * @since 3.0
     * @see [featureTupleList]
     */
    @Suppress("LeakingThis")
    @JsName("SuccessResponseOfFeatureTupleList")
    @JvmOverloads
    constructor(featureTupleList: FeatureTupleList, copy: Boolean = false) : this() {
        setFeatureTupleList(featureTupleList, copy)
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
        setTupleNumberList(tupleNumberList)
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
        setTupleNumberByteArray(tupleNumberByteArray)
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
        setTupleNumberBinary(tupleNumberBinaryArray)
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
        setTupleList(tupleList)
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
            val features = getRaw(FEATURES)
            if (features is PlatformList) return list_get_length(features)
            return _featureTupleList?.size ?: 0
        }

    companion object SuccessResponse_C {
        /**
         * The [PlatformType] of [SuccessResponse].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(SuccessResponse::class).withPackageName(PACKAGE_NAME)

        private const val FEATURES = "features"
    }

    private var _featureTupleList: FeatureTupleList? = null

    /**
     * The [feature tuples][FeatureTuple] being part of the response.
     *
     * - Reading the [featureTupleList], automatically converts the `features` into [FeatureTuple], clearing the `features`.
     * - Setting the [featureTupleList], automatically clears the `features`.
     * @since 3.0
     */
    val featureTupleList: FeatureTupleList
        get() {
            var list = _featureTupleList
            if (list != null) return list
            list = FeatureTupleList()

            // Optionally convert existing features.
            val featureList = getAs(FEATURES, NakshaFeatureList.TYPE)
            if (featureList != null) {
                list.setCapacity(featureList.size)
                for (feature in featureList) {
                    if (feature == null) continue
                    list.add(FeatureTuple(feature))
                }
            }

            _featureTupleList = list
            removeRaw(FEATURES)
            return list
        }

    /**
     * Copy the given [FeatureTuple] into [featureTupleList].
     * @param list the list of [FeatureTuple] that form the success response.
     * @since 3.0
     * @see [list]
     */
    @JsName("setFeatureTuples")
    open fun setFeatureTupleList(list: List<FeatureTuple?>): SuccessResponse {
        val featureTupleList = FeatureTupleList()
        featureTupleList.setCapacity(list.size)
        featureTupleList.addAll(list)
        _featureTupleList = featureTupleList
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
    open fun setFeatureTupleList(featureTupleList: FeatureTupleList, copy: Boolean = false) {
        val list: FeatureTupleList
        if (copy) {
            list = FeatureTupleList()
            list.setCapacity(featureTupleList.size)
            list.addAll(featureTupleList)
        } else {
            list = featureTupleList
        }
        _featureTupleList = list
    }

    /**
     * Create a response for the given [TupleNumberList], copied into [featureTupleList].
     *
     * @param tupleNumberList the [TupleNumberList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun setTupleNumberList(tupleNumberList: TupleNumberList) {
        val list = FeatureTupleList()
        list.setCapacity(tupleNumberList.size)
        for (tupleNumber in tupleNumberList) {
            if (tupleNumber == null) continue
            list.add(FeatureTuple(tupleNumber))
        }
        _featureTupleList = list
    }

    /**
     * Sets the [featureTupleList] to the decoded [tuple-number's][naksha.model.TupleNumber] read from the [TupleNumberBinaryArray], encoded in the given [ByteArray]. Basically, this will automatically wrap the given [ByteArray] into an [TupleNumberBinaryArray], and then convert it into a [FeatureTupleList].
     *
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun setTupleNumberByteArray(value: ByteArray) {
        setTupleNumberBinary(TupleNumberBinaryArray(value))
    }

    /**
     * Sets the [featureTupleList] to the decoded [tuple-number's][naksha.model.TupleNumber] read from the given [TupleNumberBinaryArray].
     *
     * This constructor will convert the binary-array into a [FeatureTupleList].
     *
     * @since 3.0
     * @see [featureTupleList]
     */
    // TODO: CASL-942 filter properties
    open fun setTupleNumberBinary(value: TupleNumberBinaryArray) {
        val list = FeatureTupleList()
        list.setCapacity(value.size)
        for (tupleNumber in value) {
            if (tupleNumber != null) {
                list.add(FeatureTuple(tupleNumber, null))
            }
        }
        _featureTupleList = list
    }

    /**
     * Create a response for the given [TupleList], copy it into [featureTupleList].
     *
     * @param tupleList the [TupleList] that form the success response.
     * @since 3.0
     * @see [featureTupleList]
     */
    open fun setTupleList(tupleList: TupleList) {
        val list = FeatureTupleList()
        list.setCapacity(tupleList.size)
        for (tuple in tupleList) {
            if (tuple == null) continue
            list.add(FeatureTuple(tuple.tupleNumber, tuple))
        }
        _featureTupleList = list
    }

    /**
     * Tests if the success response holds currently a [FeatureTupleList] instead of `features`.
     * @return _true_ if the success response holds currently a [FeatureTupleList] instead of `features`.
     * @since 3.0
     */
    fun hasFeatureTupleList(): Boolean = _featureTupleList != null

    /**
     * Tests if this response has any features, no matter if they are available already as `features` or currently pending as [FeatureTupleList].
     * @return _true_ if this response has any features.
     * @since 3.0
     */
    fun hasFeatures(): Boolean = length >= 0

    /**
     * The result converted into a list of the given type.
     *
     * Reading the features automatically convert the [featureTupleList] into feature list, clearing [featureTupleList].
     *
     * Converting the [FeatureTupleList] into a feature list can cause network IO, because when the local cache does not hold the [Tuple], the API need to load them from the storage, or any remote cache.
     * @param type The type to return the features in.
     * @return The feature list.
     * @since 3.0
     */
    override fun <F : GeoFeature, LIST : ListProxy<F>> getFeatures(type: PlatformType<LIST>): LIST {
        val raw = getRaw(FEATURES)
        if (raw is PlatformList) return type.proxy(raw)
        val list = type.newInstance()

        // Optionally convert existing feature-tuple.
        val featureTupleList = _featureTupleList
        if (featureTupleList != null) {
            featureTupleList.loadAll(acceptFeature = true)
            list.setCapacity(featureTupleList.size)
            for (tuple in featureTupleList) {
                if (tuple == null) continue
                list.add(list.elementType.proxy(tuple.feature)) // TODO (Jakub): tc0280 - adds null
            }
        }
        set(FEATURES, list)
        _featureTupleList = null
        return list
    }

    /**
     * Sets the features of the collection.
     *
     * Setting the features, automatically clears the [featureTupleList].
     * @param list The list of the features to be set.
     * @since 3.0
     * @see getFeatures
     * @see setFeatures
     * @see clearFeatures
     */
    override fun <F : GeoFeature, LIST : List<F?>> setFeatures(list: LIST) {
        set(FEATURES, ListProxy.to(GeoFeatureList.TYPE, list))
        _featureTupleList = null
    }

    override fun clearFeatures() {
        removeRaw(FEATURES)
        _featureTupleList = null
    }

    @Deprecated(
        message = "Use features property",
        replaceWith = ReplaceWith("getFeatures(NakshaFeatureList.TYPE)"),
        level = DeprecationLevel.ERROR
    )
    fun useFeatures(): NakshaFeatureList = getFeatures(NakshaFeatureList.TYPE)

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
        for (filter in filters) {
            currentList = currentList.mapNotNull { featureTuple -> filter.filter(featureTuple) }
        }

        setFeatureTupleList(currentList)
        return this
    }

    @Deprecated(
        message = "Use featureTupleList property",
        replaceWith = ReplaceWith("featureTupleList"),
        level = DeprecationLevel.ERROR
    )
    fun useFeatureTupleList(): FeatureTupleList = featureTupleList
}
