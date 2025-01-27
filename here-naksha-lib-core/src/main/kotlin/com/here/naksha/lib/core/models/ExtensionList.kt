package com.here.naksha.lib.core.models

import com.here.naksha.lib.core.models.features.Extension
import naksha.base.ListProxy

class ExtensionList: ListProxy<Extension>(Extension::class) {
    companion object ExtensionList_C {
        @JvmStatic
        fun fromList(extensions: List<Extension>): ExtensionList =
            ExtensionList().apply { addAll(extensions) }
    }
}