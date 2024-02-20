@file:OptIn(ExperimentalJsExport::class)

import com.here.naksha.lib.jbon.JbFeature
import com.here.naksha.lib.jbon.JbMap
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A feature that wraps a map.
 */
@JsExport
open class JbMapObject : JbFeature() {
    private lateinit var map: JbMap

    override fun parseHeader(mandatory: Boolean) {
        super.parseHeader(mandatory)
        check(reader.isMap())
        map = JbMap().mapReader(reader)
    }

    /**
     * Returns the reader for the embedded map.
     * @return The map reader of root.
     */
    open fun root() : JbMap = map
}