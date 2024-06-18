@file:OptIn(ExperimentalJsExport::class)

package naksha.jbon

import naksha.base.Int64
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A read-only immutable row as stored in a Naksha storage.
 * @property dictManager The dictionary manager to use to decode the JBON data.
 * @property feature The JBON encoded feature.
 * @property tags The JBON encoded tags.
 * @property xyzNs The JBON encoded XYZ namespace.
 * @property geometryType The geometry type (0=none, 1=WKB, 2=EWKB, 3=TWKB)
 */
@JsExport
@Deprecated("Please use new class from lib-model", level = DeprecationLevel.WARNING)
data class NkStorageRow(
        val dictManager: JbDictManager,
        val feature: ByteArray,
        val tags : ByteArray,
        val xyzNs : ByteArray,
        val geometryType: Short,
        val geometry : ByteArray,
        val referencePoint : ByteArray,
        val id : String,
        val uuid : String,
        val type : String,
        val fnv1aHash : Int64
)
