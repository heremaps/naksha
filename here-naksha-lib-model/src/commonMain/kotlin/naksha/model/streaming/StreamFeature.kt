package naksha.model.streaming

import naksha.base.Id
import naksha.base.PlatformMap
import naksha.base.TupleNumber
import naksha.base.Version
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * A feature of a [StreamChunk] or [StreamTransaction].
 * @since 3.0
 * @see Stream
 */
@JsExport
open class StreamFeature @JvmOverloads constructor(
    /**
     * The identifier of the feature.
     * @since 3.0
     */
    @get:JvmName("id")
    val id: Id,

    /**
     * The feature itself.
     * @since 3.0
     */
    @get:JvmName("feature")
    val feature: PlatformMap,

    /**
     * The [TupleNumber] of the feature, if the source storage does provide this.
     *
     * For Naksha systems this allows to replication, Naksha storages will always provide the tuple-number and the next-version; this may not be true for other storages.
     * @since 3.0
     */
    @get:JvmName("tupleNumber")
    val tupleNumber: TupleNumber? = null,

    /**
     * If the feature does have a [Naksha compatible next version][naksha.base.Version] in the source storage; `-1` otherwise.
     *
     * If [version] and [nextVersion] are both provided by the source storage, this allows higher write throughput, because in that case data does not need to enter _HEAD_. It can be written directly into the correct partition.
     * @since 3.0
     */
    @get:JvmName("nextVersion")
    val nextVersion: Long = -1,

    /**
     * If the feature does have a [Naksha compatible version][naksha.base.Version] in the source storage; `-1` otherwise.
     *
     * If this feature is part of a [StreamTransaction], the version must be the same as the [version of the transaction][StreamTransaction.version].
     *
     * If [version] and [nextVersion] are both provided by the source storage, this allows higher write throughput, because in that case data does not need to enter _HEAD_. It can be written directly into the correct partition.
     * @since 3.0
     */
    @get:JvmName("version")
    val version: Long = tupleNumber?.version ?: -1,

    /**
     * The optional type of the feature, if provided by the source storage.
     * @since 3.0
     */
    @get:JvmName("featureType")
    val featureType: String? = null,
)