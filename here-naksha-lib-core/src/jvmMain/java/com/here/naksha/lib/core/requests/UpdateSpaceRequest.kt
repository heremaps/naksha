package com.here.naksha.lib.core.requests

import com.here.naksha.lib.core.models.naksha.Space
import com.here.naksha.lib.core.requests.DeleteSpaceRequest.DeleteSpaceRequest_C
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.String_TYPE
import naksha.model.request.Request

// TODO: alweber: Review with Jakub. ( CASL-1205 )

/**
 * A special request send through a pipeline before a space is modified by Naksha-Hub. The event allows automatic resource management.
 *
 * ### Notes
 * - If the response to this request is an error response, and the pipeline is the pipeline of the [Space] that should be modified, then the modification is aborted (unless somehow overridden by the client with some form of force instruction).
 *
 * @since 3.0
 */
class UpdateSpaceRequest : Request() {
    companion object DeleteSpaceRequest_C {
        /**
         * The [PlatformType][naksha.base.PlatformType] of the [UpdateSpaceRequest].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(UpdateSpaceRequest::class)

        private val SPACE = NotNullProperty<UpdateSpaceRequest, Space>(Space.TYPE)
    }

    /**
     * The space that should be modified in its current state, so before applying the modification.
     * @since 3.0
     */
    var space: Space by SPACE

    /**
     * The desired new state of the space that should be persisted, the state is given before the actual modification happens and can be used to detect illegal changes.
     * @since 3.0
     */
    var newSpace: Space by SPACE
}