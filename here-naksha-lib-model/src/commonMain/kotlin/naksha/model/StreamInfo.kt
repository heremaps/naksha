@file:Suppress("OPT_IN_USAGE")

package naksha.model

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Thread safe stream information, can be extended by the application.
 * @since 3.0
 */
@JsExport
open class StreamInfo() {

    companion object StreamInfo_C {
        /**
         * The [PlatformType] of [StreamInfo].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(StreamInfo::class).withPackageName(PACKAGE_NAME)

        protected const val STREAM_ID = "streamId"
        protected const val SPACE_ID = "spaceId"
        protected const val STORAGE_ID = "storageId"
        protected const val TIME_IN_STORAGE = "timeInStorage"
    }

    /**
     * The data stored thread safe.
     * @since 3.0
     */
    protected val data = AtomicMap<String, Any>()

    /**
     * Copy the stream-information into a new platform object, so it can be JSON serialized.
     * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given type is no proxy, not instantiable, or no map.
     * @param type the [PlatformType] to return, if _null_, [AnyObject] is returned.
     * @return a copy of the stream-information, the copy is no deep copy.
     * @since 3.0
     */
    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    open fun <T : MapProxy<String,*>> toAnyObject(type: PlatformType<T>? = null): T {
        val pType = if (type == null) AnyObject.TYPE else {
            if (!type.isProxy()) throw illegalArg("The given type (${type.name}) is no proxy")
            if (!type.isInstantiatable) throw illegalArg("The given type (${type.name}) is not instantiatable")
            type
        }
        val any = pType.newInstance() as MapProxy<String, Any?>
        for (entry in data) any.set(entry.key, entry.value)
        any[TIME_IN_STORAGE] = timeInStorageMs.get()
        return any as T
    }

    /**
     * Create stream-information with specific stream-identifier.
     * @param streamId the stream-identifier to use.
     * @since 3.0
     */
    @JsName("of")
    constructor(streamId: String) : this() {
        data[STREAM_ID] = streamId
    }

    /**
     * The stream-identifier.
     * @since 3.0
     */
    var streamId: String
        get() {
            do {
                val raw = data[STREAM_ID]
                if (raw is String) return raw
                val streamId = PlatformUtil.randomString(12)
                val existing = data.putIfAbsent(STREAM_ID, streamId) ?: return streamId
                if (existing is String) return existing
                // Someone else set an invalid value, repeat (will be replaced with a valid value)
            } while (true)
        }
        internal set(value) {
            data[STREAM_ID] = value
        }

    /**
     * The identifier of the space being processed currently.
     * @since 3.0
     */
    open var spaceId: String?
        get() = data[SPACE_ID] as String?
        set(value) {
            data.putOrRemove(SPACE_ID, value)
        }

    /**
     * Set the space-id, if it is yet missing.
     * @param spaceId the space-identifier to set.
     * @return this.
     */
    open fun withSpaceIdIfMissing(spaceId: String?): StreamInfo {
        if (spaceId != null) data.putIfAbsent(SPACE_ID, spaceId)
        return this
    }

    /**
     * The storage-id being used currently.
     */
    open var storageId: String?
        get() = data[STORAGE_ID] as String?
        set(value) {
            data.putOrRemove(STORAGE_ID, value)
        }

    /**
     * Set the storage-id, if it is yet missing.
     * @param storageId the storage-identifier to set.
     * @return this.
     */
    fun withStorageIdIfMissing(storageId: String?): StreamInfo {
        if (storageId != null) data.putIfAbsent(STORAGE_ID, storageId)
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || StreamInfo::class != other::class) return false
        val that = other as StreamInfo
        return spaceId == that.spaceId && storageId == that.storageId
    }

    override fun hashCode(): Int {
        return arrayOf(spaceId, storageId).contentHashCode()
    }

    private var timeInStorageMs = AtomicInt64(0)

    /**
     * Add timer value.
     * @param deltaMillis the amount of milliseconds to add or subtract.
     * @return this.
     */
    open fun addTimeInStorage(deltaMillis: Int64): StreamInfo {
        timeInStorageMs.addAndGet(deltaMillis)
        return this
    }

    /**
     * Returns the current timer value.
     * @return the current timer value.
     */
    open fun getTimeInStorageMs(): Int64 = timeInStorageMs.get()

    /**
     * Internally used to print a value within [toColonSeparatedString].
     * @param value the value to print, or if _null_/empty prints "-".
     * @return the string to print.
     */
    protected fun print(value: Any?): String {
        if (value == null) return "-"
        if (value is String) return value.ifEmpty { "-" }
        return value.toString()
    }

    /**
     * Returns the values as string, separated by a colon.
     * @return the values as string, separated by a colon.
     */
    open fun toColonSeparatedString(): String {
        val sb = StringBuilder()
        for (entry in data) {
            sb.append(entry.key).append('=').append(print(entry.value)).append(';')
        }
        sb.append(TIME_IN_STORAGE).append('=').append(getTimeInStorageMs())
        return sb.toString()
    }
}