package naksha.model.objects

import naksha.base.Fnv1a32
import naksha.base.Platform
import naksha.geo.HereTile
import naksha.base.Action
import naksha.geo.SpGeometry
import naksha.geo.SpPoint
import naksha.model.IMemberProcessor
import naksha.model.NakshaContext
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAppId
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthor
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthorTimestamp
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzCreatedAt
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzGeometry
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzId
import naksha.model.objects.XyzMembers.XyzMembers_C.XyzReferencePoint
import kotlin.jvm.JvmStatic

/**
 * A singleton with standard processors to restore Xyz-Hub compatibility.
 *
 * To restore the functionality of Xyz-Hub, the session needs to be patched like:
 * ```kotlin
 * val processors = session.processors
 * val backup = processors.backup(clear=true)
 * try {
 *   processors.addProcessor(XyzMembers.XyzCreatedAt, XyzProcessors.xyzCreatedAt)
 *   processors.addProcessor(XyzMembers.XyzUpdatedAt, XyzProcessors.xyzUpdatedAt)
 *   processors.addProcessor(XyzMembers.XyzAppId, XyzProcessors.xyzAppId)
 *   processors.addProcessor(XyzMembers.XyzAuthor, XyzProcessors.xyzAuthor)
 *   processors.addProcessor(XyzMembers.XyzAuthorTimestamp, XyzProcessors.xyzAuthorTimestamp)
 *   // ...
 *   // perform all normal session operations.
 *   // the added processors will ensure that the members having correct values.
 * } finally {
 *   processors.restore(backup, clear=true, consume=true)
 * }
 * ```
 * @since 3.0
 */
class XyzProcessors private constructor() {
    companion object XyzProcessorsCompanion {
        /**
         * Ensures that [XyzCreatedAt][naksha.model.objects.XyzMembers.XyzMembers_C.XyzCreatedAt] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzCreatedAt = IMemberProcessor { _, memberList, feature, _, value ->
            when (value) {
                is Long -> value
                is Number -> value.toLong()
                else -> {
                    val action = memberList.useMember(StandardMembers.Tn).readTupleNumber(feature)?.action
                    if (action != Action.CREATE) XyzCreatedAt.readLong(feature) ?: Platform.currentMillis()
                    else Platform.currentMillis()
                }
            }
        }

        /**
         * Ensures that [XyzUpdatedAt][naksha.model.objects.XyzMembers.XyzMembers_C.XyzUpdatedAt] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzUpdatedAt = IMemberProcessor { _, _, _, _, _ -> Platform.currentMillis() }

        /**
         * Ensures that [XyzAppId][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAppId] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAppId = IMemberProcessor { session, _, _, _, _ ->
            session?.options?.appId ?: NakshaContext.currentContext().appId
        }

        /**
         * Ensures that [XyzAuthor][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthor] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthor = IMemberProcessor { session, _, _, _, value ->
            session?.options?.author ?: value as String? ?: NakshaContext.currentContext().author
        }

        /**
         * Ensures that [XyzAuthorTimestamp][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthorTimestamp] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthorTimestamp = IMemberProcessor { session, _, feature, _, _ ->
            val new_author: String? = session?.options?.author
                ?: XyzAuthor.get(feature)
                ?: NakshaContext.currentContext().author
            if (new_author != null) session?.getTransaction()?.time ?: Platform.currentMillis()
            else XyzAuthorTimestamp.get(feature) ?: Platform.currentMillis()
        }

        /**
         * Computes [XyzHereTile][naksha.model.objects.XyzMembers.XyzMembers_C.XyzHereTile] from the feature's
         * reference-point (or geometry centroid), falling back to the id hash when there is no geometry.
         * @since 3.0
         */
        @JvmStatic
        val xyzHereTile = IMemberProcessor { _, _, feature, _, _ ->
            val refPoint: SpGeometry? = XyzReferencePoint.get(feature) ?: XyzGeometry.get(feature)
            val point: SpPoint? = refPoint as? SpPoint ?: refPoint?.calculateCentroid()
            if (point != null) HereTile(point.latitude, point.longitude).intKey
            else {
                val id: String? = XyzId.get(feature) ?: StandardMembers.Id.get(feature)
                if (id != null) Fnv1a32.string(0, id) else 0
            }
        }

        /**
         * Computes [XyzHash][naksha.model.objects.XyzMembers.XyzMembers_C.XyzHash] for the feature.
         * @since 3.0
         */
        @JvmStatic
        val xyzHash = IMemberProcessor { _, _, feature, _, _ ->
            val id: String? = XyzId.get(feature) ?: StandardMembers.Id.get(feature)
            if (id != null) Fnv1a32.string(0, id) else 0
        }
    }
}
