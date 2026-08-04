@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.PAnyArray
import naksha.base.FeatureType
import naksha.base.Id
import naksha.base.NotNullProperty
import naksha.geo.SpBoundingBox
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.jbon.JbDictionary
import naksha.jbon.JbEncoder
import naksha.base.NakshaError.NakshaErrorCompanion.ILLEGAL_STATE
import naksha.base.NakshaException
import naksha.base.BaseUtil
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * A Naksha book.
 * @since 3.0.0
 */
@JsExport
open class NakshaBook() : NakshaFeature() {
    // TODO: We need to fix books _(previously dictionaries)_ to be able to store objects, and arrays.

    /**
     * Create a new dictionary with the given identifier
     *
     * @param id the dictionary identifier.
     * @since 3.0.0
     */
    @Suppress("LeakingThis")
    @JsName("of")
    constructor(id: Id): this() {
        this.id = id
    }

    override fun withId(value: Id?): NakshaBook = super.withId(value) as NakshaBook
    override fun withType(value: String?): NakshaBook = super.withType(value) as NakshaBook
    override fun withFeatureType(value: FeatureType?): NakshaBook = super.withFeatureType(value) as NakshaBook
    override fun featureTypeDefaultValue(): FeatureType? = FeatureType.BOOK
    override fun withBbox(value: SpBoundingBox?): NakshaBook = super.withBbox(value) as NakshaBook
    override fun withGeometry(value: SpGeometry?): NakshaBook = super.withGeometry(value) as NakshaBook
    override fun withReferencePoint(value: SpPoint?): NakshaBook = super.withReferencePoint(value) as NakshaBook
    override fun withProperties(value: NakshaProperties): NakshaBook = super.withProperties(value) as NakshaBook
    override fun withMomType(value: String?): NakshaBook = super.withMomType(value) as NakshaBook

    companion object NakshaDictionary_C {
        /**
         * The feature-type of this feature itself.
         * @since 3.0
         */
        const val FEATURE_TYPE = "naksha.Dictionary"

        /**
         * Convert the given JBON bytes into an in-memory dictionary.
         * @param bytes the byte-array with the content.
         * @return the Naksha dictionary.
         * @since 3.0.0
         */
        fun fromByteArray(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): NakshaBook
            = fromJbDictionary(JbDictionary().mapBytes(bytes, start, end))

        /**
         * Convert the given JBON bytes into an in-memory dictionary.
         * @param jbDict the JBON dictionary.
         * @return the Naksha dictionary.
         * @since 3.0.0
         */
        fun fromJbDictionary(jbDict: JbDictionary): NakshaBook {
            jbDict.loadAll()
            val id = Id(jbDict.id ?: BaseUtil.randomAtoZ())
            val dict = NakshaBook(id)
            val content = dict.content
            var i = 0
            while (i < jbDict.length) {
                content.add(jbDict.get(i++))
            }
            return dict
        }

        private val ENTRIES = NotNullProperty<NakshaBook, PAnyArray>(PAnyArray::class) { _, _ -> PAnyArray() }
    }

    /**
     * The content of the dictionary.
     *
     * Note that in the current implementation the only value acceptable is a string, but technically even objects should be allowed here. This has to be fixed in future releases.
     * @since 3.0.0
     */
    var content by ENTRIES

    /**
     * Convert this instance into a JBON encoded global dictionary.
     *
     * @return this dictionary encoded into JBON.
     * @since 3.0.0
     */
    fun toJBON(): JbDictionary {
        val encoder = JbEncoder()
        for (entry in content.withIndex()) {
            val value = entry.value
            if (value !is String) throw NakshaException(ILLEGAL_STATE, "Currently only dictionaries with strings are supported")
            encoder.addToLocalDictionary(value)
        }
        val bytes = encoder.buildDictionary(id.text)
        return JbDictionary().mapBytes(bytes)
    }
}