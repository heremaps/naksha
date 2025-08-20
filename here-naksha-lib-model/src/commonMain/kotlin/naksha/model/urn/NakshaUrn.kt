@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.NakshaError.NakshaError_C.ILLEGAL_ARGUMENT
import naksha.base.NakshaException
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Abstract base class for all Naksha URNs. They store the location of the entity they refer to in the `branch` component. All Naksha entities have their types prefixed with `"naksha."`, for example `naksha.Collection`, `naksha.Map`, ... and use the domain `naksha`. The general syntax for Naksha URNs is:
 *
 * `urn:here:{storage}.{map}.{collection}:naksha:{type}:{id}`
 *
 * Specialized syntax forms are _(for the administrative containers)_:
 * - `urn:here:{storage}.{map}:naksha:naksha.Collection:{id}`
 * - `urn:here:{storage}:naksha:naksha.Map:{id}`
 * - `urn:here::naksha:naksha.Storage:{id}`
 *
 * Specialized forms for the storage global entities are:
 * - `urn:here:{storage}:naksha:naksha.Transaction:{id}`
 * - `urn:here:{storage}:naksha:naksha.Dictionary:{id}`
 *
 * @param urnParts The URN parts.
 * @param urnString The stringified URN, if `null`, it will be generated on demand.
 * @since 3.0
 * @see AbstractHereUrn
 */
@JsExport
abstract class NakshaUrn<SELF> protected constructor(urnParts: Array<String>, urnString: String?)
    : AbstractHereUrn<SELF>(urnParts, urnString) {

    /**
     * The parts of the location (aka `branch`), split by dot _(`.`)_, being in order: `storage-id`, `map-id`, `collection-id`, optional from right to left, may even be empty, so without parts, when a storage is referred.
     * @since 3.0
     */
    protected var branchParts: List<String>? = null
        get() {
            if (field == null) {
                field = get(BRANCH).split(".")
            }
            return field
        }

    /**
     * Create a Naksha URN from another Here URN.
     * @param urn Any other Here URN.
     * @since 3.0
     */
    @JsName("NakshaUrn_fromUrn")
    constructor(urn: AbstractHereUrn<*>) : this(urnPartsOf(urn), urnValueOf(urn)) {
        if (urn is NakshaUrn<*>) this.branchParts = urn.branchParts
    }

    companion object NakshaUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [NakshaUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(NakshaUrn::class).withPackageName(PACKAGE_NAME)

        // TODO: alweber: Another Kotlin compiler bug, when we make this protected, compiler fails always.
        //       Either it complains that it is not annotated with JvmState or it fails, because it is annotated!
        const val STORAGE_ID = 0
        const val MAP_ID = 1
        const val COLLECTION_ID = 2
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        if (!NAKSHA.equals(get(DOMAIN), true)) return false
        if (branchParts == null) return false
        return true
    }

    protected fun nakshaPart(i: Int): String {
        val p = branchParts ?: illegalState()
        if (i < 0 || i >= p.size) throw NakshaException(ILLEGAL_ARGUMENT, "Invalid index: $i")
        val v = p[i]
        return v
    }

    /**
     * The domain _(should always be `"naksha"`, if [isValid])_.
     * @since 3.0
     */
    val domain: String
        get() = get(DOMAIN)
}