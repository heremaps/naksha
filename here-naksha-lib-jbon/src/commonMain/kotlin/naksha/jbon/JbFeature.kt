package naksha.jbon

import naksha.base.Binary
import naksha.base.BinaryView
import kotlin.js.JsExport

/**
 * A mapper that allows reading a JBON feature. After mapping, the [reader] can be used to access the content of the
 * feature. Beware that the content of an JBON feature can be anything, but most often will be a map. To read this
 * kind of features, simply use the [JbMapFeature] class.
 * @constructor Create a new feature reader.
 * @param binaryView The binary to map initially.
 * @property dictManager The dictionary manager to use to decode the feature.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class JbFeature(var dictManager: IDictManager, binaryView: BinaryView = Binary.EMPTY_IMMUTABLE) : JbStruct<JbFeature>(binaryView) {
    private var id: String? = null
    private var featureType: Int = -1

    override fun clear(): JbFeature {
        super.clear()
        id = null
        featureType = -1
        return this
    }

    override fun parseHeader() {
        check(unitType == TYPE_FEATURE) { "Mapped structure is no feature, but ${JbReader.unitTypeName(unitType)}" }
        // The id of global dictionary (optional).
        if (reader.isString()) {
            val dictId = reader.decodeString()
            reader.globalDict = dictManager.getDictionary(dictId)
            check(reader.globalDict != null) { "Unable to load necessary dictionary '$dictId'" }
        } else {
            check(reader.isNull()) { "Expected dictionary ID to be either a string or null, but found ${JbReader.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to feature-id field" }
        // The feature-id (optional).
        if (reader.isString()) {
            id = reader.decodeString()
        } else {
            check(reader.isNull()) { "Expected feature-id to be either a string or null, but found ${JbReader.unitTypeName(reader.unitType())}" }
        }
        check(reader.nextUnit()) { "Failed to seek forward to local dictionary field" }
        // The embedded local dictionary.
        check(reader.isDictionary()) { "Expect local dictionary, but found ${JbReader.unitTypeName(reader.unitType())}" }
        reader.localDict = JbDict(reader.binary).mapReader(reader)
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