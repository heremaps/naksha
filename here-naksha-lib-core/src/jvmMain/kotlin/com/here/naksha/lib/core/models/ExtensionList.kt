package com.here.naksha.lib.core.models

import com.here.naksha.lib.core.models.features.Extension
import naksha.base.ListProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType

class ExtensionList: ListProxy<Extension>(Extension.TYPE) {
    companion object ExtensionList_C {
        /**
         * The [PlatformType] of [ExtensionList].
         * @since 3.0
         */
        @JvmField
        val TYPE = forKClass(ExtensionList::class)

        @JvmStatic
        fun fromList(extensions: List<Extension>): ExtensionList =
            ExtensionList().apply { addAll(extensions) }
    }
}