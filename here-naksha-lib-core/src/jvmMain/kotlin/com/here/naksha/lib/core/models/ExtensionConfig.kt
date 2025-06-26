package com.here.naksha.lib.core.models

import com.here.naksha.lib.core.models.features.Extension
import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass

class ExtensionConfig() : AnyObject() {
    companion object {
        /**
         * The [PlatformType] of [ExtensionConfig].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(ExtensionConfig::class)

        private val EXTENSIONS_NULL = NullableProperty<ExtensionConfig, ExtensionList>(ExtensionList.TYPE)
        private val WHITELIST_DELEGATE_CLASSES_NULL = NullableProperty<ExtensionConfig, StringList>(StringList.TYPE)
        private val LONG = NotNullProperty<ExtensionConfig, Long>(Long_TYPE)
        private val STRING = NotNullProperty<ExtensionConfig, String>(String_TYPE)
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