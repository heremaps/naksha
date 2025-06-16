@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.MapProxy
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import naksha.base.String_TYPE
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map that combines operations to be executed for multiple services.
 *
 * This can be used as a helper by services or applications implementing multiple logical services. For example the HERE Wikvaya service implements the UPM, the Map-Feedback-API, and a few other logical services, and therefore may want to test user rights against operations from different services at ones.
 * @since 3.0
 * @see UserRightsMatrix
 * @see ServiceOps
 */
@JsExport
class ServiceOpsMatrix : MapProxy<String, ServiceOps>(String_TYPE, ServiceOps.TYPE) {
    companion object ServiceOpsMatrix_C {
        /**
         * The [PlatformType] of [ServiceOpsMatrix].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ServiceOpsMatrix> = forKClass(ServiceOpsMatrix::class).withPackageName(PACKAGE_NAME)
    }
}