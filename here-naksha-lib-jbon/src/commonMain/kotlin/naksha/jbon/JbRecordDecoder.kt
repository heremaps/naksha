package naksha.jbon

import kotlin.js.JsExport

/**
 * A mapper that allows reading a JBON feature. After mapping, the [reader] can be used to access the content of the feature. Beware that the content of an JBON feature can be anything, but most often will be a map. To read this kind of features, simply use the [JbFeatureDecoder] class.
 * @constructor Create a new feature reader.
 * @property dictReader the dictionary reader to use to decode the feature.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class JbRecordDecoder(var dictReader: IDictReader? = null) : JbStructDecoder<JbRecordDecoder>() {
    private var id: String? = null
    private var featureType: Int = -1

    /**
     * The dictionary-id of the global dictionary that is needed; if any is needed, otherwise `null`.
     * @since 3.0
     */
    var globalDictionaryId: String? = null
        private set

    /**
     * Returns the global dictionary, if this record requires one.
     * - Throws [IllegalStateException] if a dictionary is required, but none could be loaded.
     * @return the global dictionary, if this record requires one.
     */
    val globalDictionary: JbDictionary?
        get() {
            val dictId = globalDictionaryId ?: return null
            var dictionary = reader.globalDict
            if (dictionary == null) {
                dictionary = dictReader?.getDictionary(dictId)
                if (dictionary != null) reader.globalDict = dictionary
            }
            check(dictionary != null) { "Unable to load necessary dictionary '$dictId'" }
            return dictionary
        }

    override fun clear(): JbRecordDecoder {
        super.clear()
        id = null
        featureType = -1
        return this
    }

    override fun onMap() {
        check(unitType == TYPE_FEATURE) { "Mapped structure is no feature, but ${JbDecoder.unitTypeName(unitType)}" }
        globalDictionaryId = if (reader.isString()) reader.decodeString() else null
    }

    override fun doParseHeader() {
        val globalDictId = globalDictionaryId
        if (globalDictId != null) {
            reader.globalDict = globalDictionary
            check(reader.globalDict != null) { "Unable to load necessary dictionary '$globalDictId'" }
        } else {
            check(reader.isNull()) { "Expected dictionary ID to be either a string or null, but found ${JbDecoder.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to feature-id field" }
        // The feature-id (optional).
        if (reader.isString()) {
            id = reader.decodeString()
        } else {
            check(reader.isNull()) { "Expected feature-id to be either a string or null, but found ${JbDecoder.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to local dictionary field" }
        // The embedded local dictionary.
        check(reader.isDictionary()) { "Expect local dictionary, but found ${JbDecoder.unitTypeName(reader.unitType())}" }
        reader.localDict = JbDictionary().mapReader(reader)
        check(reader.nextUnit()) { "Failed to seek forward to the feature payload" }
        featureType = reader.unitType()
    }

    /**
     * Returns the **id** of the feature, if any is encoded.
     * @return The (optional) **id** of the feature.
     */
    fun id(): String? {
        return id
    }

    /**
     * Returns the feature type.
     * @return The feature type.
     */
    fun featureType(): Int {
        return featureType
    }
}