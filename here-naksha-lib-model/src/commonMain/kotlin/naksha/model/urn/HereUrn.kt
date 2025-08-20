@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.Platform.Platform_C.decodeURIComponent
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A helper to split a [URN](https://www.rfc-editor.org/rfc/rfc8141) in the [Here](https://www.here.com/) URN format, as specified in the [Map Object Model](https://www.here.com/learn/blog/unimap-map-object-model) format into its parts.
 *
 * A typical use case would be:
 * ```kotlin
 * val urn = HereUrn(
 *   "urn:here:q23.18:here:foo.Feature:world"
 * )
 * assert urn.isValid()
 * assert urn.branch == "q23.18"
 * assert urn.domain == "here"
 * assert urn.featureType == "foo.Feature"
 * assert urn.contentId == "world"
 * ```
 * @since 3.0
 * @see AbstractHereUrn
 */
@JsExport
class HereUrn private constructor(
    parts: Array<String>,
    urnValue: String? = null
) : AbstractHereUrn<HereUrn>(parts, urnValue) {
    companion object HereUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [AbstractHereUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(HereUrn::class).withPackageName(PACKAGE_NAME)

        private fun Array<String>.decodeURIComponent() {
            for (i in this.indices) {
                this[i] = decodeURIComponent(this[i])
            }
        }

        private fun Array<String>.fix(): Array<String> {
            if (size >= 3
                && "urn".equals(this[0], true)
                && "here".equals(this[1], true)
            ) {
                // lowercase first parts.
                this[SCHEME] = "urn"
                this[NID] = "here"
            }
            return this
        }

        private fun splitUrn(urn: String, decode: Boolean): Array<String> {
            val parts = urn.split(":").toTypedArray()
            if (decode) parts.decodeURIComponent()
            return parts.fix()
        }

        private fun copyParts(parts: Array<out String>, decode: Boolean): Array<String> {
            val copy = Array(parts.size) { if (decode) decodeURIComponent(parts[it]) else parts[it] }
            return copy.fix()
        }

        private fun copyList(parts: List<String>, decode: Boolean): Array<String> {
            val copy = Array(parts.size) { if (decode) decodeURIComponent(parts[it]) else parts[it] }
            return copy.fix()
        }
    }

    /**
     * Create a URN from another URN, sharing the same underlying parts (zero copy constructor).
     * @param other The other HERE URN.
     * @since 3.0
     */
    @JsName("fromUrn")
    constructor(other: AbstractHereUrn<*>) : this(urnPartsOf(other), urnValueOf(other))

    /**
     * Create a URN from the given parts.
     * @param parts The list of parts.
     * @param decode If the parts should be URI component decoded.
     * @since 3.0
     */
    @JsName("fromList")
    constructor(parts: List<String>, decode: Boolean) : this(copyList(parts, decode))

    /**
     * Create a URN from the given parts.
     * @param parts The array of parts.
     * @param decode If the parts should be URI component decoded.
     * @since 3.0
     */
    @JsName("fromParts")
    constructor(vararg parts: String, decode: Boolean) : this(copyParts(parts, decode))

    /**
     * Create a URN from the given string.
     * @param urn The URN string to split.
     * @since 3.0
     */
    @JsName("fromString")
    constructor(urn: String) : this(splitUrn(urn, true))

    /**
     * The branch.
     * @since 3.0
     */
    val branch: String?
        get() {
            val v = get(BRANCH)
            return if (v.isEmpty()) null else v
        }

    /**
     * The domain.
     * @since 3.0
     */
    val domain: String
        get() {
            val v = get(DOMAIN)
            return if (v.isEmpty()) "here" else v
        }

    /**
     * The content-id.
     * @since 3.0
     */
    val contentId: String
        get() = get(CONTENT_ID)
}