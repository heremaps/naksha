package com.here.naksha.lib.core.requests

import com.here.naksha.lib.core.models.naksha.Space
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.request.Request

// TODO: alweber: Review with Jakub. ( CASL-1205 )

/**
 * A special request send through a pipeline after a new space was created by Naksha-Hub. The event allows automatic resource management.
 *
 * ### Notes
 * - If the response to this request is an error response, this error is returned to the client, but the space creation is not reverted.
 * - The space that was created is given in the [space] property of this event, it can be the same as the `eventTarget` of the handler, but it doesn't have to, because we can listen to these events as well in subscriptions or other pipelines.
 *
 * @since 3.0
 */
class CreateSpaceRequest : Request() {
    companion object DeleteSpaceRequest_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] of the [CreateSpaceRequest].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(CreateSpaceRequest::class)

        private val SPACE = NotNullProperty<CreateSpaceRequest, Space>(Space.TYPE)
    }

    /**
     * The space that was created.
     * @since 3.0
     */
    var space: Space by SPACE
}