package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * An enumeration about the possible well-known error codes returned by storages. Every storage can provide own special errors by simply
 * extend this class.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
open class StorageError : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = StorageError::class

    override fun initClass() {}

    companion object {
        /**
         * Returned when an already initialized storage is initialized, providing a wrong identifier.
         */
        @JsStatic
        @JvmField
        val STORAGE_ID_MISMATCH = def(StorageError::class, 0x0001) {
            self -> self.defaultMessage = "Storage identifier does not match the provided expected one"
        }
    }

    /**
     * The error-code of this error.
     */
    val code: Int = if (value is Int) value as Int else toString().hashCode()

    /**
     * The default message to use, if no explicit message provided, when throwing an [StorageException].
     */
    var defaultMessage: String? = null
        protected set
}