@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.Int64
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.ObjectProxy
import kotlin.DeprecationLevel.WARNING
import kotlin.js.JsExport

/**
 * The XYZ namespace stored in [properties.@ns:com:here:xyz][NakshaPropertiesProxy.XYZ] of the [NakshaFeatureProxy].
 */
@JsExport
class XyzProxy : ObjectProxy() {
    companion object {
        private val ACTION = NotNullProperty<Any, XyzProxy, String>(String::class) { _, _ -> ActionEnum.CREATED.str }
        private val STRING = NotNullProperty<Any, XyzProxy, String>(String::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val STRING_NULL = NullableProperty<Any, XyzProxy, String>(String::class)
        private val INT = NotNullProperty<Any, XyzProxy, Int>(Int::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val INT64 = NotNullProperty<Any, XyzProxy, Int64>(Int64::class) { _, name ->
            throw IllegalStateException("The field $name must have a value")
        }
        private val INT64_NULL = NullableProperty<Any, XyzProxy, Int64>(Int64::class)
        private val TAGS = NotNullProperty<Any, XyzProxy, TagsProxy>(TagsProxy::class)
        private val DELTA_PROXY = NullableProperty<Any, XyzProxy, NakshaDeltaProxy>(NakshaDeltaProxy::class)
        private val REFERENCES = NullableProperty<Any, XyzProxy, XyzReferencesProxy>(XyzReferencesProxy::class)
    }

    /**
     * The universal unique identifier of the state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, but does not store a real UUID, but rather a GUID (global unique identifier).
     */
    var uuid: String by STRING

    /**
     * The universal unique identifier of the previous state of a feature.
     *
     * This field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set, when history is enabled for the layer.
     * - **Data Hub**: This field is set, when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field is always set, but does not store a real UUID, but rather a GUID (global unique identifier).
     */
    var puuid: String? by STRING_NULL

    /**
     * The universal unique identifier of the state of the feature that was used to merge with the previous state to produce this state.
     *
     * This happens when concurrent modifications are done, but an automatic merge was possible. This field is populated by Interactive
     * API, Data Hub or XYZ Hub. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: Does not support this field, it will always be _null_.
     */
    @Deprecated("This field is not supported by Naksha, but part of MOM specification", level = WARNING)
    var muuid: String? by STRING_NULL

    /**
     * The time when this feature was created.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     */
    var createdAt: Int64 by INT64

    /**
     * The last time when this feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated by Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Always sets this field.
     */
    var updatedAt: Int64 by INT64

    /**
     * The space in which this feature is located.
     *
     * This field is populated by Interactive API, Data Hub and XYZ Hub. It always represents the current space where the feature resides
     * and is automatically set when persisting a feature. Any values provided by the user are overwritten.
     * - **Interactive API**: Always sets this field.
     * - **Data Hub**: Always sets this field.
     * - **XYZ Hub**: Always sets this field.
     * - **Naksha**: Does not support this field, it will always be _null_.
     */
    @Deprecated("This field is not supported by Naksha, but part of MOM specification", level = WARNING)
    var space: String? by STRING_NULL

    /**
     * Customer defined tags for this feature.
     *
     * This field is populated by the client.
     * - **Interactive API**: Does not change the value of this field.
     * - **Data Hub**: Can add or remove some values, depending on the use of query parameters addTags and removeTags.
     * - **XYZ Hub**: Can add or remove some values, depending on the use of the query parameters addTags and removeTags.
     * - **Naksha**: Allows event-handlers in the pipeline to modify the values. The values are interpreted, they have an intrinsic
     * specific encoding and are split for indexing, so they encode a key-value pair, and the value can be searched (e.g. `name=Foo` or
     * `age:=5`). The server guarantees that when two tags have the same key, they are collapsed, by the later version overriding the
     * previous one.
     */
    var tags: TagsProxy by TAGS

    /**
     * The version of the feature.
     *
     * Multiple features could be part of a single version if they have been edited in one transaction. This field is populated by
     * Interactive API, Data Hub, XYZ Hub and Naksha. Any values provided by the user are overwritten.
     * - **Interactive API**: This field is set when history is enabled for the layer.
     * - **Data Hub**: This field is set when history or UUID is enabled for the space.
     * - **XYZ Hub**: This field is set when history or UUID is enabled for the space.
     * - **Naksha**: This field stores the transaction-number (`txn`).
     */
    var version: Int64 by INT64

    /**
     * The change-count, so how often the feature has been changed since it was created. The value starts with 1.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var changeCount: Int by INT

    /**
     * The change that was applied to the feature, being CREATED, UPDATED or DELETED.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var action: String by ACTION

    /**
     * The identifier of the application that modified the feature the last.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var appId: String by STRING

    /**
     * The author of the feature. Not every change of feature is done by intention, the author is only set, when the change of the
     * feature was done by intention and not as a side effect. For example, repair bots will not claim authorship, but cause the [appId]
     * to change.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var author: String? by STRING_NULL

    /**
     * The time when this author of the feature was modified.
     *
     * The value is a valid Unix timestamp which is the number of milliseconds since January 1st, 1970, leap seconds are ignored. This
     * field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var authorTs: Int64? by INT64_NULL

    /**
     * The [FNV1a](https://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function) hash above the feature, calculated server
     * side.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var fnva1: Int64 by INT64

    /**
     * The origin of the feature.
     *
     * The value is a GUID as defined by **Naksha**, and describes from where the feature comes originally. The field is only set, when
     * the feature was forked, for example, when a topology is split, the children will all have the `origin` set to the GUID of the
     * feature that was originally split. If the children are split again, their `origin` will again refer to the feature that was split,
     * effectively creating a tree of changes.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var origin: String? by STRING_NULL

    /**
     * The HERE tile-id in which the reference-point of the feature is located at level 14.
     *
     * This field is populated only by **Naksha**. Any values provided by the user are overwritten.
     */
    var geoGrid: Int by INT

    var deltaProxy: NakshaDeltaProxy? by DELTA_PROXY

    /**
     * References to MOM objects.
     */
    var references: XyzReferencesProxy? by REFERENCES

    fun useDeltaNamespace(): NakshaDeltaProxy {
        if (deltaProxy == null) {
            deltaProxy = NakshaDeltaProxy()
        }
        return deltaProxy!!
    }
}