package naksha.model.request

import naksha.model.IReadRowFilter
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Base request class.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class Request(
    /**
     * true - no feature body will be returned in the response, nor as [ByteArray], nor as feature object.
     */
    val noFeature: Boolean = false,
    /**
     *  true - no geometry will be returned in the response, nor as [ByteArray], nor in `feature.geo attribute`.
     */
    val noGeometry: Boolean = false,
    /**
     *  true - no geometry reference point will be returned in the response.
     */
    val noGeoRef: Boolean = false,
    /**
     * true - no metadata will be returned in the response, nor as [ByteArray], nor in `feature.properties.xyz` attribute.
     */
    val noMeta: Boolean = false,
    /**
     * true - no tags will be returned in the response, nor as [ByteArray], nor in `feature.properties.xyz.tags` attribute.
     */
    val noTags: Boolean = false,
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
    val resultFilter: Array<IReadRowFilter> = emptyArray()
)