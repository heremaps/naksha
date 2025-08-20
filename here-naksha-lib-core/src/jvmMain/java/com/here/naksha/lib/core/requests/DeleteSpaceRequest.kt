package com.here.naksha.lib.core.requests

import com.here.naksha.lib.core.models.naksha.Space
import naksha.base.NotNullProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.model.request.Request

// TODO: alweber: Review with Jakub. ( CASL-1205 )

/**
 * A special request send through a pipeline before a space is deleted by Naksha-Hub. The event allows automatic resource management.
 *
 * ### Notes
 * - If the response to this request is an error response, and the pipeline is the pipeline of the [Space] that should be deleted, then the deletion is aborted (unless somehow overridden by the client with some form of force instruction).
 *
 * @since 3.0
 */
class DeleteSpaceRequest : Request() {
    companion object DeleteSpaceRequest_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] of the [DeleteSpaceRequest].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(DeleteSpaceRequest::class)

        private val SPACE = NotNullProperty<DeleteSpaceRequest, Space>(Space.TYPE)
    }

    /**
     * The space that should be deleted.
     * @since 3.0
     */
    var space: Space by SPACE
}