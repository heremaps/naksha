package naksha.base

@JsExport
actual open class ForEachAbort internal actual constructor(value: Any?) : Exception() {
    actual val value: Any? = value
}