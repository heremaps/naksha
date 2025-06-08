@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import naksha.model.objects.NakshaFeature
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of MOM references.
 * @since 3.0
 */
@JsExport
class MomReferenceList : ListProxy<MomReference>(MomReference.TYPE) {
    companion object MomReferenceListCompanion {
        /**
         * The [PlatformType] of [MomReferenceList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomReferenceList::class).withPackageName(PACKAGE_NAME)
    }
}
