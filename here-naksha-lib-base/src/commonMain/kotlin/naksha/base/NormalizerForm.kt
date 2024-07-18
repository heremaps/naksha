package naksha.base

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Please refer []Unicode Normalization Forms](https://www.unicode.org/reports/tr15/#Norm_Forms)
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
enum class NormalizerForm {

    /**
     * Canonical decomposition.
     */
    NFD,

    /**
     * Canonical decomposition, followed by canonical composition.
     */
    NFC,

    /**
     * Compatibility decomposition.
     */
    NFKD,

    /**
     * Compatibility decomposition, followed by canonical composition.
     */
    NFKC
}