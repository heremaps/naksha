@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.Platform.Platform_C.forKClass
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A special Here URN that refers to a [Naksha feature][naksha.model.objects.NakshaFeature].
 *
 * `urn:here:{storage}.{map}.{collection}:naksha:{type}:{id}`
 * @since 3.0
 * @see AbstractHereUrn
 */
@JsExport
class NakshaFeatureUrn private constructor(urnParts: Array<String>, urnValue: String?)
    : NakshaUrn<NakshaCollectionUrn>(urnParts, urnValue) {

    @JsName("NakshaFeatureUrn_fromUrn")
    constructor(urn: AbstractHereUrn<*>) : this(urnPartsOf(urn), urnValueOf(urn))

    @JsName("NakshaFeatureUrn_fromValues")
    constructor(storageId: String, mapId: String, collectionId: String, id: String)
            : this(arrayOf("urn", "here", "$storageId.$mapId.$collectionId", NAKSHA, NakshaFeature.TYPE_STRING, id), null)

    companion object NakshaFeatureUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [NakshaFeatureUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(NakshaFeatureUrn::class).withPackageName(PACKAGE_NAME)
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
        get() = nakshaPart(COLLECTION_ID)

    /**
     * The feature-id.
     * @since 3.0
     */
    val featureId: String
        get() = get(CONTENT_ID)
}