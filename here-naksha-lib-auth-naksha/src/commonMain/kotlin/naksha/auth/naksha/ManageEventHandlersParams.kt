@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.NullableProperty
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import naksha.base.StringList
import naksha.model.TagMap
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * The parameters for the [NakshaOps.manageEventHandlers] operation. This operation is executed whenever a user tries to modify an event-handler, so create, update, or delete.
 * @since 3.0
 * @see ManageEventHandlersParamsList
 * @see NakshaOps.manageEventHandlers
 */
@JsExport
class ManageEventHandlersParams(): NakshaParams() {

    /**
     * Create parameters for [NakshaOps.manageEventHandlers], so the user wants to modify an event-handler.
     * @param eventHandler The event-handler to be modified.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(eventHandler: NakshaFeature) : this() { // TODO: We should have a concrete NakshaEventHandler feature!
        fromFeature(eventHandler)
        className = eventHandler.getAs("className", String_TYPE)
    }

    companion object ManageEventHandlerCompanion {
        /**
         * The [PlatformType] of [ManageEventHandlersParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ManageEventHandlersParams> = forKClass(ManageEventHandlersParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<ManageEventHandlersParams, String>(String_TYPE, autoRemove = true)
    }

    override fun withId(id: String?): ManageEventHandlersParams = super.withId(id) as ManageEventHandlersParams
    override fun withAppId(appId: String?): ManageEventHandlersParams = super.withAppId(appId) as ManageEventHandlersParams
    override fun withAuthor(author: String?): ManageEventHandlersParams = super.withAuthor(author) as ManageEventHandlersParams
    override fun withTags(tags: TagMap?): ManageEventHandlersParams = super.withTags(tags) as ManageEventHandlersParams
    override fun withSpaceIds(spaceIds: StringList?): ManageEventHandlersParams = super.withSpaceIds(spaceIds) as ManageEventHandlersParams

    /**
     * The full qualified class-name of the event-handler implementation.
     * @since 3.0
     */
    var className: String? by STRING_MEMBER

    fun withClassName(className: String?): ManageEventHandlersParams {
        this.className = className
        return this
    }
}