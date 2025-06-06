@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.*
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.model.TagMap
import naksha.model.objects.NakshaStorage
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Describe a storage that is being [used][NakshaOps.useStorages] or [modified][NakshaOps.manageSpaces].
 * @since 3.0
 * @see NakshaOps.useStorages
 * @see NakshaOps.manageStorages
 */
@JsExport
class StorageParams() : NakshaParams() {

    /**
     * Initialize these parameters from the given feature.
     * @param storage The storage from which to auto-set parameters.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(storage: NakshaStorage) : this() {
        fromFeature(storage)
        className = storage.className
    }

    companion object StorageResourceCompanion {
        /**
         * The [PlatformType] of [StorageParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<StorageParams> = forKClass(StorageParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<StorageParams, String>(String_TYPE, autoRemove = true)
    }

    override fun withId(id: String?): StorageParams = super.withId(id) as StorageParams
    override fun withAppId(appId: String?): StorageParams = super.withAppId(appId) as StorageParams
    override fun withAuthor(author: String?): StorageParams = super.withAuthor(author) as StorageParams
    override fun withTags(tags: TagMap?): StorageParams = super.withTags(tags) as StorageParams
    override fun withSpaceIds(spaceIds: StringList?): StorageParams = super.withSpaceIds(spaceIds) as StorageParams

    /**
     * The full qualified class-name of the storage implementation.
     * @since 3.0
     */
    var className: String? by STRING_MEMBER

    fun withClassName(className: String?): StorageParams {
        this.className = className
        return this
    }
}