@file:Suppress("OPT_IN_USAGE")

package naksha.model.objects

import naksha.base.ListProxy
import kotlin.js.JsExport

/**
 * A list of Naksha features.
 */
@JsExport
open class NakshaFeatureList : ListProxy<NakshaFeature>(NakshaFeature::class)
