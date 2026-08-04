package com.here.naksha.lib.core.models.features

import naksha.base.Id
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.model.objects.NakshaFeature

class Extension() : NakshaFeature() {
    companion object {
        private val STRING_NULL = NullableProperty<Extension, String>(String::class)
        private val STRING = NotNullProperty<Extension, String>(String::class)
    }

    var url by STRING

    var version by STRING

    var initClassName by STRING_NULL

    var env by STRING_NULL

    constructor(
        id: Id,
        url: String,
        version: String,
        initClassName: String?,
    ) : this() {
        this.id = id
        this.url = url
        this.version = version
        this.initClassName = initClassName
    }
}