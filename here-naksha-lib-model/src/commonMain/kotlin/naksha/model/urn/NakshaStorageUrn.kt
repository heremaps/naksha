@file:Suppress("OPT_IN_USAGE")

package naksha.model.urn

import naksha.base.Platform.Platform_C.forKClass
import naksha.model.objects.NakshaCollection
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A Here URN that refers to a [NakshaStorage][naksha.model.objects.NakshaStorage].
 *
 * `urn:here::naksha:naksha.Storage:{id}`
 * @since 3.0
 * @see AbstractHereUrn
 */
@JsExport
class NakshaStorageUrn private constructor(urnParts: Array<String>, urnValue: String?)
    : NakshaUrn<NakshaStorageUrn>(urnParts, urnValue) {

    @JsName("NakshaStorageUrn_fromUrn")
    constructor(urn: AbstractHereUrn<*>) : this(urnPartsOf(urn), urnValueOf(urn))

    @JsName("NakshaStorageUrn_fromValues")
    constructor(storageId: String)
            : this(arrayOf("urn", "here", "", NAKSHA, NakshaStorage.TYPE_STRING, storageId), null)


    companion object NakshaStorageUrn_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] for [NakshaStorageUrn].
         * @since 3.0
         */
        @JsStatic
        @JvmField
        val TYPE = forKClass(NakshaStorageUrn::class).withPackageName(PACKAGE_NAME)
    }

    override fun isValid(): Boolean {
        if (!super.isValid()) return false
        return featureType == NakshaStorage.TYPE.jsonType
    }

    /**
     * The storage-id.
     * @since 3.0
     */
    val storageId: String
        get() = get(CONTENT_ID)
}