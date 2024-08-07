@file:Suppress("OPT_IN_USAGE")

package naksha.model.mom

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of MOM references.
 */
@JsExport
class MomReferenceList : ListProxy<MomReference>(MomReference::class)