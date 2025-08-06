@file:Suppress("OPT_IN_USAGE")

package naksha.auth

import naksha.base.*
import naksha.base.Platform.Platform_C.forKClass
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The URM _(User Rights Matrix)_ is a map where the key represents the `id` of a service for which a user has rights to execute operations, and the value represent the rights of the user for that service.
 *
 * The URM is normally returned by the UPM _(User Permission Management)_, and therefore normally only parsed from the authentication context using [fromJSON]. The main function of URM is [matches], which checks whether the given [ServiceOps] are allowed for the user.
 *
 * Assume the following URM is received for the current user:
 * ```js
 * { // User-Rights-Matrix
 *   naksha: { // 'naksha' User-Rights
 *     // The 'read-features' operation of Naksha
 *     readFeatures: [ // User-Rights-Filters
 *       // A single User-Rights-Filter
 *       {
 *         // 'id' needs to start with 'prefix-'
 *         id: "prefix-*",
 *         // 'storageId' must be equal to 'foo'
 *         storageId: "foo",
 *         // must have a tag that starts with 't1-',
 *         // and another tag being exactly 't2'
 *         tags: {op:"matchesKey", allOf:["t1-*", "t2"]}
 *       }
 *     ]
 *   },
 *   mapFeedback: { // 'map-feedback' User-Rights
 *     ...
 *   },
 *   moderation: { // 'moderation' User-Rights
 *   }
 * }
 * ```
 *
 * Kotlin example of compact pre-execution usage:
 * ```kotlin
 * // When the request is received:
 * val urm = UserRightsMatrix.fromJSON(urmJson)
 *
 * // Update features
 * val features = ...
 * val ops = NakshaOps()
 * for (f in features) {
 *   ops.updateFeatures += WriteFeatureOp.fromFeature(f)
 * }
 * if (!nakshaRights.matches(ops)) {
 *   throw AccessDenied()
 * }
 * // Execute the write
 * ```
 *
 * Kotlin example of fine-grained post-execution usage:
 * ```kotlin
 * // When the request is received:
 * val urm = UserRightsMatrix.fromJSON(urmJson)
 * val nakshaRights = urm["naksha"]
 *
 * // Read features
 * val features = readFeatures()
 *
 * // Filter features
 * for (f in features) {
 *   val ops = NakshaOps()
 *   ops.readFeatures += ReadFeatureOp.fromFeature(f)
 *   if (!nakshaRights.matches(ops)) features.remove(f)
 * }
 *
 * // Now, "features" does only contain those features
 * // the user has access to.
 * ```
 * It is as well possible to execute a fine-grained pre-execution filter, for example to improve the error messages.
 * @since 3.0
 * @see UserRights
 */
@JsExport
class UserRightsMatrix : MapProxy<String, UserRights>(String_TYPE, UserRights.TYPE) {
    companion object UserRightsMatrix_C {
        /**
         * The [PlatformType] of [UserRightsMatrix].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(UserRightsMatrix::class).withPackageName(PACKAGE_NAME)

        /**
         * Parse the given JSON and return the [URM][UserRightsMatrix].
         * - Throws [NakshaError.ILLEGAL_ARGUMENT], if the given `json` is invalid and no [URM][UserRightsMatrix].
         * @param json The JSON that stores the [URM][UserRightsMatrix].
         * @return the successfully parsed [URM][UserRightsMatrix].
         * @since 3.0
         */
        @JvmStatic
        @JsStatic
        fun fromJSON(json: String): UserRightsMatrix {
            return Platform.fromJson(json, TYPE) ?: throw illegalArg("Invalid URM JSON given")
        }
    }

    /**
     * Tests if the user is allowed to execute all operations of all given services.
     * @param serviceOpsMatrix The operations of services that the user wants to execute.
     * @return _true_ if the user is allowed to execute all operations of all services; _false_ otherwise.
     * @since 3.0
     */
    fun matches(serviceOpsMatrix: ServiceOpsMatrix): Boolean {
        for (serviceName in serviceOpsMatrix.keys) {
            val serviceOps = serviceOpsMatrix[serviceName] ?: continue
            val userRights = this[serviceName] ?: return false
            if (!userRights.matches(serviceOps)) return false
        }
        return true
    }

    /**
     * Tests if the user is allowed to execute operations of a specific service.
     * @param operations The operations the user wants to execute.
     * @param serviceName Optionally overrides [ServiceOps.serviceName].
     * @return _true_ if the user is allowed to execute all given operations; _false_ otherwise.
     * @since 3.0
     */
    @JsName("matchesOps")
    @JvmOverloads
    fun matches(operations: ServiceOps, serviceName: String? = null): Boolean {
        val key = serviceName ?: operations.serviceName
        val userRights = this[key] ?: return false
        return userRights.matches(operations)
    }

}