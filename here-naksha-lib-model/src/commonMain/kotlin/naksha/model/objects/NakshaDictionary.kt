@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.*
import naksha.geo.BBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.jbon.JbDictionary
import naksha.jbon.JbEncoder
import naksha.base.NakshaError.NakshaError_C.ILLEGAL_STATE
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

// TODO: We need to fix dictionaries to be able to store objects, and arrays.

/**
 * A Naksha dictionary.
 * @since 3.0
 * @see NakshaObject
 * @see NakshaStorage
 * @see NakshaMap
 * @see NakshaCollection
 * @see NakshaDictionary
 * @see NakshaSubscriptionState
 * @see NakshaTx
 */
@JsExport
open class NakshaDictionary() : NakshaObject() {

    /**
     * Create a new dictionary with the given identifier
     *
     * @param id the dictionary identifier.
     * @since 3.0
     */
    @JsName("NakshaDictionaryOf")
    constructor(id: String): this() {
        this.id = id
    }

    companion object NakshaDictionary_C {
        /**
         * The [PlatformType] of [NakshaDictionary].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(NakshaDictionary::class)
            .withPackageName(PACKAGE_NAME)
            .withJsonType("naksha.Dictionary")

        /**
         * The feature-type of this feature itself.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Dictionary"

        /**
         * Convert the given JBON bytes into an in-memory dictionary.
         * @param bytes the byte-array with the content.
         * @return the Naksha dictionary.
         * @since 3.0
         */
        fun fromByteArray(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): NakshaDictionary
            = fromJbDictionary(JbDictionary().mapBytes(bytes, start, end))

        /**
         * Convert the given JBON bytes into an in-memory dictionary.
         * @param jbDict the JBON dictionary.
         * @return the Naksha dictionary.
         * @since 3.0
         */
        fun fromJbDictionary(jbDict: JbDictionary): NakshaDictionary {
            jbDict.loadAll()
            val id = jbDict.id
            val dict = if (id == null) NakshaDictionary() else NakshaDictionary(id)
            val content = dict.content
            var i = 0
            while (i < jbDict.length) {
                content.add(jbDict.get(i++))
            }
            return dict
        }

        private val ENTRIES = NotNullProperty<NakshaDictionary, AnyList>(AnyList.TYPE) { _, _ -> AnyList() }
    }

    override val properties: NakshaProperties
        get() = get_properties(NakshaProperties.TYPE)

    /**
     * The content of the dictionary.
     *
     * Note that in the current implementation the only value acceptable is a string, but technically even objects should be allowed here. This has to be fixed in future releases.
     * @since 3.0
     */
    var content by ENTRIES

    /**
     * Convert this instance into a JBON encoded global dictionary.
     *
     * @return this dictionary encoded into JBON.
     * @since 3.0
     */
    fun toJbon(): JbDictionary {
        val encoder = JbEncoder()
        for (entry in content.withIndex()) {
            val value = entry.value
            if (value !is String) throw NakshaException(ILLEGAL_STATE, "Currently only dictionaries with strings are supported")
            encoder.addToLocalDictionary(value)
        }
        val bytes = encoder.buildDictionary(id)
        return JbDictionary().mapBytes(bytes)
    }
}