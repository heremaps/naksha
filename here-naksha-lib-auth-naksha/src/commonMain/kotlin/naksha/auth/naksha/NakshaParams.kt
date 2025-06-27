@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.auth.ServiceOpParams
import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.NakshaContext
import naksha.model.TagMap
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The general params that all Naksha operations require. In Naksha all operations do handle some kind of features, therefore they all share the basic parameters `id`, `appId`, `author`, `tags`, and `spaceIds`.
 * @since 3.0
 * @see StorageParams
 * @see SpaceParams
 * @see UseEventHandlersParams
 * @see ManageEventHandlersParams
 */
@JsExport
open class NakshaParams() : ServiceOpParams() {
    /**
     * Initialize these parameters from the given feature.
     * @param feature The feature from which to auto-set parameters.
     * @return this.
     * @since 3.0
     */
    @JsName("NakshaParamsOf")
    constructor(feature: NakshaFeature) : this() {
        fromFeature(feature)
    }

    companion object NakshaParams_C {
        /**
         * The [PlatformType] of [NakshaParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<NakshaParams> = forKClass(NakshaParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<NakshaParams, String>(String_TYPE, autoRemove = true)
        private val STRING_LIST_MEMBER = NotNullProperty<NakshaParams, StringList>(StringList.TYPE) { _,_ -> StringList() }
        private val TAG_MAP_MEMBER = NotNullProperty<NakshaParams, TagMap>(TagMap.TYPE) { _,_ -> TagMap() }
    }

    /**
     * Default implementation to import the general [NakshaParams] from the given feature.
     * @param feature The [NakshaFeature] from which to import.
     */
    protected fun fromFeature(feature: NakshaFeature) {
        id = feature.id
        val xyz = feature.properties.xyz
        appId = xyz.appId
        author = xyz.author
        tags.copyFrom(xyz.tags)
        val ctx = NakshaContext.currentContext<NakshaContext>()
        spaceIds.addAll(ctx.spaceIds)
    }

    open fun withId(id: String?): NakshaParams {
        this.id = id
        return this
    }
    open fun withAppId(appId: String?): NakshaParams {
        this.appId = appId
        return this
    }
    open fun withAuthor(author: String?): NakshaParams {
        this.author = author
        return this
    }
    open fun withTags(tags: TagMap?): NakshaParams {
        if (tags != null) this.tags = tags else this.tags.clear()
        return this
    }
    open fun withSpaceIds(spaceIds: StringList?): NakshaParams {
        this.spaceIds.clear()
        if (spaceIds != null) this.spaceIds.addAll(spaceIds)
        return this
    }

    /**
     * The `id` of the feature.
     * @since 3.0
     */
    var id: String? by STRING_MEMBER

    /**
     * The `appId` of application that last modified the feature.
     * @since 3.0
     */
    var appId: String? by STRING_MEMBER

    /**
     * The `author` that last modified the feature.
     * @since 3.0
     */
    var author: String? by STRING_MEMBER

    /**
     * The `tags` of the feature as [TagMap].
     * @since 3.0
     * @see naksha.model.objects.NakshaFeature.properties.xyz.tags
     */
    var tags: TagMap by TAG_MAP_MEMBER

    /**
     * The space context in which this operation is executed.
     *
     * Will be an empty list, if the operation is executed from outside a space pipeline.
     *
     * This allows to ensure that a user can only perform certain operations in a specific space context, for example the user must only create features using a specific space, to ensure that certain handlers are added, that ensure integrity, or alike.
     * @since 3.0
     * @see naksha.model.NakshaContext.spaceIds
     */
    val spaceIds: StringList by STRING_LIST_MEMBER
}