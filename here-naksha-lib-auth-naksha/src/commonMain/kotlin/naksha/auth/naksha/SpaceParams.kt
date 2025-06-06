@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.NotNullProperty
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.base.StringList
import naksha.model.TagMap
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Describes the space being part of an [useSpaces][NakshaOps.useSpaces] or an [manageSpaces][NakshaOps.manageSpaces] operation.
 * @since 3.0
 * @see NakshaOps.useSpaces
 * @see NakshaOps.manageSpaces
 */
@JsExport
class SpaceParams() : NakshaParams() {

    /**
     * Initialize parameters from the given space feature.
     * @param space The space from which to auto-set parameters.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(space: NakshaFeature) : this() { // TODO: We should have a concrete NakshaSpace feature!
        fromFeature(space)

        val ids = space.getAs("eventHandlerIds", StringList.TYPE)
        if (!ids.isNullOrEmpty()) {
            eventHandlerIds.addAll(ids)
        }
    }

    companion object SpaceResourceCompanion {
        /**
         * The [PlatformType] of [SpaceParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<SpaceParams> = forKClass(SpaceParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_LIST_MEMBER = NotNullProperty<SpaceParams, StringList>(StringList.TYPE) { _, _ -> StringList() }
    }

    override fun withId(id: String?): SpaceParams = super.withId(id) as SpaceParams
    override fun withAppId(appId: String?): SpaceParams = super.withAppId(appId) as SpaceParams
    override fun withAuthor(author: String?): SpaceParams = super.withAuthor(author) as SpaceParams
    override fun withTags(tags: TagMap?): SpaceParams = super.withTags(tags) as SpaceParams
    override fun withSpaceIds(spaceIds: StringList?): SpaceParams = super.withSpaceIds(spaceIds) as SpaceParams

    /**
     * The `id`'s of all event-handlers being added to the space in the order in which they are added.
     * @since 3.0
     */
    val eventHandlerIds: StringList by STRING_LIST_MEMBER

    fun withEventHandlerIds(vararg ids: String): SpaceParams {
        eventHandlerIds.addAll(ids)
        return this
    }
}