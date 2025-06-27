package naksha.jbon

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_contains_key
import naksha.base.PlatformMapApi.PlatformMapApi_C.map_set
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A feature is a record, where the root unit is a map.
 * @constructor Create a new feature reader for records with a map as body.
 * @param dictReader the dictionary reader to use.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class JbFeatureDecoder(dictReader: IDictReader? = null) : JbRecordDecoder(dictReader) {

    companion object JbFeatureDecoder_C {
        /**
         * The [PlatformType] of [JbFeatureDecoder].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(JbFeatureDecoder::class).withPackageName(PACKAGE_NAME)
    }

    private lateinit var _map: JbMapDecoder

    override fun clear(): JbFeatureDecoder {
        super.clear()
        if (this::_map.isInitialized) _map.clear()
        return this
    }

    override fun doParseHeader() {
        super.doParseHeader()
        check(reader.isMap()) { "Failed to parse feature payload, expected map, but found ${JbDecoder.unitTypeName(reader.unitType())}" }
        if (!this::_map.isInitialized) _map = JbMapDecoder()
        _map.mapReader(reader)
    }

    /**
     * Returns the reader for the embedded map.
     * @return The map reader of root.
     */
    open fun root(): JbMapDecoder = _map

    private fun startMap(): PlatformMap? {
        val id = id()
        if (id != null) {
            val map = Platform.newMap()
            map_set(map, "id", id)
            return map
        }
        return null
    }

    /**
     * Decode the feature into [AnyObject].
     * @return the map.
     */
    open fun toAnyObject(): AnyObject
        = AnyObject.TYPE.proxy(root().toPlatformMap(startMap()))

    /**
     * Decode the feature into the given type.
     * @param type The type into which to decode the feature.
     * @return the decoded feature.
     */
    open fun <V, T: MapProxy<String, V>> to(type: PlatformType<T>): T
        = type.proxy(root().toPlatformMap(startMap()))

    /**
     * Auto-detect the type of the feature and return it.
     * @return The feature auto-boxed or `null`, if auto-boxing fails.
     */
    @Suppress("UNCHECKED_CAST")
    open fun <T: MapProxy<String, *>> box(): T {
        val pMap = root().toPlatformMap(startMap())
        return (Platform.box(pMap, Any_TYPE) ?: throw illegalState("Failed to auto-box feature")) as T
    }

    private fun splitJsonPath(jsonPath: String): Array<Any> {
        // Regular expression to match parts of the JSON path
        val parts = jsonPath.split(".")
        val out = Array(parts.size) {
            var part: Any = parts[it]
            try {
                part = parts[it].toInt()
            } catch (_: NumberFormatException) {
            }
            part
        }
        return out
    }

    /**
     * Reads the value using the given path.
     *
     * Value being returned are:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Int64`
     * - `Double`
     * - `PlatformMap`
     * - `PlatformList`
     * - `PlatformDataView`
     *
     * @param path the path to select, strings are used to enter maps, integers are used to select from arrays.
     * @return either the value read from the path or [Platform.UNDEFINED][naksha.base.Platform.UNDEFINED], when the path does not exist.
     */
    open operator fun get(vararg path: Any): Any? {
        reset() // Move the reader to the root-map.
        if (!_selectPath(reader, 0, path)) return Platform.UNDEFINED
        return reader.decodeValue()
    }

    /**
     * Reads the value using the given path.
     *
     * Value being returned are:
     * - `null`
     * - `Boolean`
     * - `Int`
     * - `Int64`
     * - `Double`
     * - `PlatformMap`
     * - `PlatformList`
     * - `PlatformDataView`
     *
     * @param path the path to select (`properties.test`), strings are used to enter maps, integers are used to select from arrays.
     * @return either the value read from the path or [Platform.UNDEFINED][naksha.base.Platform.UNDEFINED], when the path does not exist.
     */
    open fun getJsonPath(path: String): Any? = get(*splitJsonPath(path))

    /**
     * Moves the cursor to given path.
     *
     * If the select succeeds, [`decoder.reader.unitType()`][naksha.jbon.JbDecoder.unitType] can be used to detect what the value is, or [`decoder.reader.decodeValue()`][naksha.jbon.JbDecoder.decodeValue] can be used to simply decode the value. Beware, when the value is a complex type (Map, Array), decoding is more expensive than maybe necessary, so maybe it is better to just test the unit-type!
     *
     * @param path the path to select, strings are used to enter maps, integers are used to select from arrays.
     * @return _true_ if the path was selected, and exists; _false_ otherwise.
     */
    open fun selectPath(vararg path: Any): Boolean {
        reset() // Move the reader to the root-map.
        return _selectPath(reader, 0, path)
    }

    private tailrec fun _selectPath(r: JbDecoder, i: Int, path: Array<out Any>): Boolean {
        if (i >= path.size) return true
        val pkey = path[i]
        @Suppress("CascadeIf")
        if (pkey is String) {
            if (r.unitType() != TYPE_MAP) return false
            val end = r.pos + r.unitSize()
            r.enterStruct()
            while (r.pos < end) {
                // Keys are always dictionary references.
                val index = r.decodeRef()
                val dict = if (r.isGlobalRef()) r.globalDict else r.localDict
                check(dict != null) { "Missing dictionary for key-reference: $index" }
                val key = dict.get(index)
                // Skip over key
                r.nextUnit()
                // If the key was what we wanted
                if (pkey == key) return _selectPath(r, i + 1, path)
                // Otherwise, skip value as well
                r.nextUnit()
            }
            // Not found
            return false
        } else if (pkey is Int) {
            if (r.unitType() != TYPE_ARRAY) return false
            val end = r.pos + r.unitSize()
            r.enterStruct()
            var index = 0
            while (r.pos < end) {
                if (pkey == index) return _selectPath(r, i + 1, path)
                // Skip over value
                r.nextUnit()
                index++
            }
            // Not found
            return false
        } else return false
    }

    /**
     * Returns the feature as arbitrary map.
     * @return the feature as map.
     */
    fun toMap(): AnyObject {
        val feature = _map.toAnyObject()
        if (id() != null && "id" !in feature) feature.set("id", id())
        return feature
    }

    /**
     * Returns the feature as specific map.
     * @param T the proxy type to return.
     * @return the feature as T.
     */
    fun <T: AnyObject> proxy(type: PlatformType<T>): T = type.proxy(toMap())
}