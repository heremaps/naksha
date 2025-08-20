@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.Platform.Platform_C.forKClass
import naksha.model.objects.NakshaMap
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A Here URN that refers to a [NakshaCollection][naksha.model.objects.NakshaCollection].
 *
 * `urn:here:{storage}:naksha:naksha.Map:{id}`
 * @since 3.0
 * @see AbstractHereUrn
 */
@JsExport
class NakshaMapUrn private constructor(urnParts: Array<String>, urnValue: String?)
    : NakshaUrn<NakshaCollectionUrn>(urnParts, urnValue) {

    @JsName("NakshaMapUrn_fromUrn")
    constructor(urn: AbstractHereUrn<*>) : this(urnPartsOf(urn), urnValueOf(urn))

    @JsName("NakshaMapUrn_fromValues")
    constructor(storageId: String, mapId: String)
            : this(arrayOf("urn", "here", storageId, NAKSHA, NakshaMap.TYPE_STRING, mapId), null)

    companion object NakshaMapUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [NakshaMapUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(NakshaMapUrn::class).withPackageName(PACKAGE_NAME)
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return featureType == NakshaMap.TYPE.jsonType
    }

    /**
     * The storage-id.
     * @since 3.0
     */
    val storageId: String
        get() = nakshaPart(STORAGE_ID)

    /**
     * The map-id.
     * @since 3.0
     */
    val mapId: String
        get() = get(CONTENT_ID)
}