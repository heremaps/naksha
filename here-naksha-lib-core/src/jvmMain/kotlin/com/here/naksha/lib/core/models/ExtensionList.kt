package com.here.naksha.lib.core.models

import com.here.naksha.lib.core.models.features.Extension
import naksha.base.PTypedArray

class ExtensionList: PTypedArray<Extension>(Extension::class) {
    companion object ExtensionList_C {
        @JvmStatic
        fun fromList(extensions: List<Extension>): ExtensionList =
            ExtensionList().apply { addAll(extensions) }
    }
}