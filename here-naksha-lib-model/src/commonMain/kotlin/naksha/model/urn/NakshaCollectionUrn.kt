@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.Platform.Platform_C.forKClass
import naksha.model.objects.NakshaCollection
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A Here URN that refers to a [NakshaCollection][naksha.model.objects.NakshaCollection].
 *
 * `urn:here:{storage}.{map}:naksha:naksha.Collection:{id}`
 * @since 3.0
 * @see NakshaUrn
 * @see AbstractHereUrn
 */
@JsExport
class NakshaCollectionUrn private constructor(urnParts: Array<String>, urnValue: String?)
    : NakshaUrn<NakshaCollectionUrn>(urnParts, urnValue) {

    @JsName("NakshaCollectionUrn_fromUrn")
    constructor(urn: AbstractHereUrn<*>) : this(urnPartsOf(urn), urnValueOf(urn))

    @JsName("NakshaCollectionUrn_fromValues")
    constructor(storageId: String, mapId: String, collectionId: String)
            : this(arrayOf("urn", "here", "$storageId.$mapId", NAKSHA, NakshaCollection.TYPE_STRING, collectionId), null)

    companion object NakshaCollectionUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [NakshaCollectionUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(NakshaCollectionUrn::class).withPackageName(PACKAGE_NAME)
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return featureType == NakshaCollection.TYPE.jsonType
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
        get() = nakshaPart(MAP_ID)

    /**
     * The collection-id.
     * @since 3.0
     */
    val collectionId: String
        get() = get(CONTENT_ID)
}