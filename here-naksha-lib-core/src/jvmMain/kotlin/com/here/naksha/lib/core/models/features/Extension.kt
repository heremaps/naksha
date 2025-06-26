package com.here.naksha.lib.core.models.features

import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import naksha.model.AbstractStorage
import naksha.model.objects.NakshaFeature

class Extension() : NakshaFeature() {
    companion object {
        /**
         * The [PlatformType] of [Extension].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(Extension::class)

        private val STRING_NULL = NullableProperty<Extension, String>(String_TYPE)
        private val STRING = NotNullProperty<Extension, String>(String_TYPE)
    }

    var url by STRING

    var version by STRING

    var initClassName by STRING_NULL

    constructor(
        id: String,
        url: String,
        version: String,
        initClassName: String?
    ) : this() {
        this.id = id
        this.url = url
        this.version = version
        this.initClassName = initClassName
    }
}