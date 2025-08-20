@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.*
import naksha.base.NakshaError.NakshaError_C.INVALID_URN_FORMAT
import naksha.base.Platform.Platform_C.encodeURIComponent
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.bugs.KT_68775_infinite_loop_for_calling_super_getter
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A abstract base class for all URN that are in the HERE format, defined as:
 *
 * `urn:here:{branch}:{domain}:{feature-type}:{content-id}`
 *
 * Therefore, a Here URN has always exactly 6 parts. The meaning of the parts differ slightly, dependent on to which [featureType] the URN refers to.
 * @since 3.0
 * @see HereUrn
 * @see NakshaStorageUrn
 * @see NakshaMapUrn
 * @see NakshaCollectionUrn
 * @see NakshaFeatureUrn
 * @see NakshaGuidUrn
 */
@JsExport
open class AbstractHereUrn<SELF> protected constructor(
    /**
     * The decoded URN parts.
     * @since 3.0
     */
    private val urnParts: Array<String>,

    /**
     * The internal stringified URN, if available.
     * @since 3.0
     */
    private var urnString: String? = null
 // TODO: alweber: Extend with r-, q-, and f-component parts.
) {

    companion object HereUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [AbstractHereUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(AbstractHereUrn::class).withPackageName(PACKAGE_NAME)

        /**
         * The index of the scheme (always being `"urn"`) within the [urnParts].
         * @since 3.0
         */
        const val SCHEME = 0

        /**
         * The index of the [Namespace Identifier](https://www.rfc-editor.org/rfc/rfc8141#section-2.1) (always being `"here"`) within the [urnParts].
         * @since 3.0
         */
        const val NID = 1

        /**
         * The index of the `branch` within the [urnParts]. The meaning of the branch depends on the [featureType].
         * @since 3.0
         */
        const val BRANCH = 2

        /**
         * The index of the `domain` within the [urnParts]. The meaning of the domain depends on the [featureType].
         * @since 3.0
         */
        const val DOMAIN = 3

        /**
         * The index of the `domain` within the [urnParts]. The meaning of the domain depends on the [featureType].
         * @since 3.0
         */
        const val FEATURE_TYPE = 4

        /**
         * The index of the `content-id` within the [urnParts]. The meaning of the content-id depends on the [featureType].
         * @since 3.0
         */
        const val CONTENT_ID = 4

        /**
         * Returns the internal parts array for extending classes to forward it to abstract URN constructor.
         * @param urn the URN instance from which to extract the value.
         * @return the internal parts array.
         * @since 3.0
         */
        @JvmStatic
        protected fun urnPartsOf(urn: AbstractHereUrn<*>): Array<String> = urn.urnParts

        /**
         * Returns the internal stringified URN for extending classes to forward it to abstract URN constructor.
         * @param urn the URN instance from which to extract the value.
         * @return the internal stringified URN.
         * @since 3.0
         */
        @JvmStatic
        protected fun urnValueOf(urn: AbstractHereUrn<*>): String? = urn.urnString
    }

    /**
     * The URN as string, the same as calling [toString].
     * @since 3.0
     */
    val urn: String
        get() = get_urn()

    @KT_68775_infinite_loop_for_calling_super_getter
    protected open fun get_urn(): String {
        var urn = urnString
        if (urn == null) {
            urn = urnParts.joinToString(":") { encodeURIComponent(it) }
            urnString = urn
        }
        return urn
    }

    /**
     * Tests if this URN is a valid HERE URN.
     * @return _true_ if this is generally a valid HERE URN; _false_ otherwise.
     */
    open fun isValid(): Boolean {
        return urnParts.size >= 3
            && "urn".equals(urnParts[SCHEME], true)
            && "here".equals(urnParts[NID], true)
    }

    protected fun illegalState(): Nothing {
        throw NakshaException(INVALID_URN_FORMAT, "Invalid URN format, must start with 'urn:here': $urn")
    }

    /**
     * Tests if this URN is valid, if being valid, returns itself. If not being valid, throws [INVALID_URN_FORMAT].
     * @return this.
     * @since 3.0
     */
    @Suppress("UNCHECKED_CAST")
    fun validate(): SELF {
        if (!isValid()) illegalState()
        return this as SELF
    }

    /**
     * Returns the amount of parts of the URN.
     * @return the amount of parts of the URN.
     * @since 3.0
     */
    fun size(): Int = urnParts.size

    /**
     * Returns a specific part.
     * @param i The index of the part, a value between 0 and [size] - 1.
     * @return the part.
     */
    operator fun get(i: Int): String = urnParts[i]

    /**
     * The scheme, must always be `"urn"`.
     * @since 3.0
     */
    val scheme: String
        get() = "urn"

    /**
     * The namespace identifier, must always be `"here"`.
     * @since 3.0
     */
    val nid: String
        get() = "here"

    /**
     * The branch.
     * @since 3.0
     */
    val featureType: String
        get() = urnParts[FEATURE_TYPE]

    /**
     * Return the detected platform type; if any.
     * @since 3.0
     */
    val platformType: PlatformType<*>?
        get() = Platform.forFirstJsonType(featureType, Any_TYPE)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AbstractHereUrn<*>
        return urnParts.contentEquals(other.urnParts)
    }

    private var hashCode: Int? = null
    override fun hashCode(): Int {
        var hc = this.hashCode
        if (hc == null) {
            hc = urnParts.contentHashCode()
            this.hashCode = hc
        }
        return hc
    }

    override fun toString(): String = urn
}