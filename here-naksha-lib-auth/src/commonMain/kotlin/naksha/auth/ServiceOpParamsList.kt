@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.ListProxy
import naksha.base.Platform.PlatformCompanion.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A list of [parameter-set's][ServiceOpParams] for an operation.
 *
 * Each execution of an operation has its own [parameter-set][ServiceOpParams], therefore, each operation in a [ServiceOps] map has a [list of parameter-set's][ServiceOpParamsList] with one [parameter-set][ServiceOpParams] per operation execution.
 *
 * @since 3.0
 * @see ServiceOpParams
 * @see ServiceOps
 */
@JsExport
open class ServiceOpParamsList<E: ServiceOpParams>(elementType: PlatformType<E>) : ListProxy<E>(elementType) {
    companion object ServiceOpParamsListCompanion {
        /**
         * The [PlatformType] of [ServiceOpParamsList].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ServiceOpParamsList<*>> = forKClass(ServiceOpParamsList::class).withPackageName(PACKAGE_NAME)
    }
}