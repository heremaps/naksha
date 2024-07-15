@file:Suppress("OPT_IN_USAGE")

package naksha.model.request

import naksha.base.fn.Fn1
import kotlin.js.JsExport
import kotlin.jvm.JvmField

/**
 * Base request class.
 */
@JsExport
abstract class Request<SELF : Request<SELF>> {

    /**
     * true - no feature body will be returned in the response, nor as [ByteArray], nor as feature object.
     */
    @JvmField
    var noFeature: Boolean = false

    fun withNoFeature(): Request<SELF> {
        this.noFeature = true
        return this
    }

    /**
     *  true - no geometry will be returned in the response, nor as [ByteArray], nor in `feature.geo attribute`.
     */
    @JvmField
    var noGeometry: Boolean = false

    fun withNoGeometry(): Request<SELF> {
        this.noGeometry = true
        return this
    }

    /**
     *  true - no geometry reference point will be returned in the response.
     */
    @JvmField
    var noGeoRef: Boolean = false

    fun withNoGeoRef(): Request<SELF> {
        this.noGeoRef = true
        return this
    }

    /**
     * true - no metadata will be returned in the response, nor as [ByteArray], nor in `feature.properties.xyz` attribute.
     */
    @JvmField
    var noMeta: Boolean = false

    fun withNoMeta(): Request<SELF> {
        this.noMeta = true
        return this
    }

    /**
     * true - no tags will be returned in the response, nor as [ByteArray], nor in `feature.properties.xyz.tags` attribute.
     */
    @JvmField
    var noTags: Boolean = false

    fun withNoTags(): Request<SELF> {
        this.noTags = true
        return this
    }

    /**
     * The resultFilter is a list of lambdas, that are invoked by the storage for every row that should be added into the results of the
     * response. The method can inspect the row and should return either the unmodified row, or a modified version to be added to the
     * response or null, if the row should be removed from the response.
     *
     * The filter lambdas are called in LIFO order (last in, first out). The output each the lambda is used as input for the next one.
     * So,  only if all return a valid new row, the last returned row will be added to the response. This means, each filter can modify
     * the row or cause it to be removed from the response.
     *
     * Beware that the filters are not serializable, therefore they can only be executed with a storage in the same process and not with
     * foreign storages.
     *
     * - When [noFeature] is set, the feature is not read from the database (`row.feature` will be null).
     * - When [noGeometry] is set, the geometry is not read from the database (`row.geometry` will be null).
     * - When [noGeoRef] is set, the reference-point is not read from the database (`row.geo_ref` will be null).
     * - When [noTags] is set, the tags are not read from the database (`row.tags` will be null).
     * - When [noMeta] is set, no meta-data is read from the database (`row.meta` will be null).
     *
     * Beware: If the filters ([noFeature], [noGeometry], [noGeoRef], [noTags] or [noMeta]) are set, these values are not even read from
     * the database and therefore the result-filters will not find these values either!
     */
    @JvmField
    var resultFilter: MutableList<Fn1<ResultRow, ResultRow>> = mutableListOf()

    fun addResultFilter(filter: Fn1<ResultRow, ResultRow>): Request<SELF> {
        this.resultFilter.add(filter)
        return this
    }

    /**
     * Copy all properties of this request into the given target and return the target.
     * @param copy the target to receive the copy.
     * @return the given copy target.
     */
    open fun copyTo(copy: SELF): SELF {
        copy.noFeature = this.noFeature
        copy.noGeometry = this.noGeometry
        copy.noGeoRef = this.noGeoRef
        copy.noMeta = this.noMeta
        copy.noTags = this.noTags
        copy.resultFilter = this.resultFilter.toMutableList()
        return copy
    }
}