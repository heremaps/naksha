@file:Suppress("OPT_IN_USAGE")

package naksha.auth.naksha

import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
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
 * The parameters for the [NakshaOps.useEventHandlers] operation. This operation is executed, when a user tries to add an event-handler into a space.
 * @since 3.0
 * @see UseEventHandlersParamsList
 * @see NakshaOps.useEventHandlers
 */
@JsExport
class UseEventHandlersParams(): NakshaParams() {

    /**
     * Create parameters for [NakshaOps.useEventHandlers], so the user wants to add an event-handler into a space.
     * @param eventHandler The event-handler to be added to a space.
     * @param spaceId The `id` of the space into which the event handler should be added.
     * @return this.
     * @since 3.0
     */
    @JsName("of")
    constructor(eventHandler: NakshaFeature, spaceId: String) : this() { // TODO: We should have a concrete NakshaEventHandler feature!
        fromFeature(eventHandler)
        className = eventHandler.getAs("className", String_TYPE)
        this.spaceId = spaceId
    }

    companion object EventHandlerResource_C {
        /**
         * The [PlatformType] of [UseEventHandlersParams].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<UseEventHandlersParams> = forKClass(UseEventHandlersParams::class).withPackageName(PACKAGE_NAME)

        private val STRING_MEMBER = NullableProperty<UseEventHandlersParams, String>(String_TYPE, autoRemove = true)
    }

    override fun withId(id: String?): UseEventHandlersParams = super.withId(id) as UseEventHandlersParams
    override fun withAppId(appId: String?): UseEventHandlersParams = super.withAppId(appId) as UseEventHandlersParams
    override fun withAuthor(author: String?): UseEventHandlersParams = super.withAuthor(author) as UseEventHandlersParams
    override fun withTags(tags: TagMap?): UseEventHandlersParams = super.withTags(tags) as UseEventHandlersParams
    override fun withSpaceIds(spaceIds: StringList?): UseEventHandlersParams = super.withSpaceIds(spaceIds) as UseEventHandlersParams

    /**
     * The full qualified class-name of the event-handler implementation.
     * @since 3.0
     */
    var className: String? by STRING_MEMBER

    fun withClassName(className: String?): UseEventHandlersParams {
        this.className = className
        return this
    }

    /**
     * The `id` of the space to which the event-handler should be added.
     * @since 3.0
     */
    var spaceId: String? by STRING_MEMBER

    fun withSpaceId(spaceId: String?): UseEventHandlersParams {
        this.spaceId = spaceId
        return this
    }
}