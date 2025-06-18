@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * A map of operation invocations, so the operations a user wants to perform.
 *
 * The key is the name of the operation that should be executed _(for example `readFeatures`, `writeFeatures`, ...)_, the value is the [list of parameters][ServiceOpParamsList], with which each operation should be executed. If the same operation is executed multiple times with different parameters, one [parameter-set][ServiceOpParams] per execution should be added into the [parameter list][ServiceOpParamsList].
 *
 * This map is build by an application or service to describe what operations a user want to execute, so that it can invoke [UserRights.matches] against the [ServiceOps] to verify if the user is allowed to execute all the operations the way he wants.
 *
 * Generally it is recommended that the service create the [ServiceOps], and [match][UserRights.matches] it, before executing the operation, especially for operations that perform mutations _(for example `updateFeatures` or `deleteFeatures`)_. Theoretically, in a transactional context, it would be possible to execute the operation, then to verify it, and eventually to either _commit_ or _rollback_, but it's clearly much more expensive, then pre-verification.
 *
 * However, if the operation is a read-only operation _(for example `readFeatures`)_, it is often not possible to [match][UserRights.matches] the operation against the [ServiceOps] before executing the operation. For example, for an `readFeatures` operation it is not possible to verify the details of the feature _(like tags)_, before the feature was read. Therefore, a service may first want to actually read the features, and then remove all features the user does not have access too. Please see [UserRightsMatrix] documentation for an example.
 *
 * @since 3.0
 * @see ServiceOpParamsList
 * @see ServiceOpsMatrix
 * @see UserRights
 */
@JsExport
open class ServiceOps : AnyObject() {
    companion object ServiceOps_C {
        /**
         * The [PlatformType] of [ServiceOps].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE: PlatformType<ServiceOps> = forKClass(ServiceOps::class).withPackageName(PACKAGE_NAME)
    }

    /**
     * The service-name of the service to which this operation-set belongs.
     *
     * The default implementation lowercases the first character of the class-name, and removes an optional `Ops` suffix, so `NakshaOps` becomes `naksha`, `MapCreatorOps` becomes `mapCreator`, `UpmOps` becomes `upm`, aso.
     *
     * This property is runtime only, so it is not serialized and can't be found in the underlying serialized form. It should be overridden by extending classes, if the default rule about classname to `serviceName` relation does not apply.
     * @since 3.0
     */
    val serviceName: String = forKClass(this::class).simpleName.removeSuffix("Ops").replaceFirstChar { it.lowercase() }
}