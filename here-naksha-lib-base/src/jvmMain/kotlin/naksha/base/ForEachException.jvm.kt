package naksha.base

actual open class ForEachAbort internal actual constructor(value: Any?): Exception(null, null, false, false) {
    @JvmField
    actual val value: Any? = value
}