package com.here.naksha.lib.core.models

import com.here.naksha.lib.core.models.features.Extension
import naksha.base.AnyObject
import naksha.base.NotNullProperty
import naksha.base.NullableProperty
import naksha.base.StringList

class ExtensionConfig() : AnyObject() {
    companion object {
        private val EXTENSIONS_NULL = NullableProperty<ExtensionConfig, ExtensionList>(ExtensionList::class)
        private val WHITELIST_DELEGATE_CLASSES_NULL = NullableProperty<ExtensionConfig, StringList>(StringList::class)
        private val LONG = NotNullProperty<ExtensionConfig, Long>(Long::class)
        private val STRING = NotNullProperty<ExtensionConfig, String>(String::class)
    }

    var expiry by LONG
    var extensions by EXTENSIONS_NULL
    var whitelistDelegateClasses by WHITELIST_DELEGATE_CLASSES_NULL
    var env by STRING

    constructor(expiry: Long,
        extensions: List<Extension>,
                whitelistDelegateClasses: List<String>,
        env: String) : this() {
        this.expiry = expiry
        this.extensions = ExtensionList.fromList(extensions)
        this.whitelistDelegateClasses = StringList.fromList(whitelistDelegateClasses)
        this.env = env
    }
}