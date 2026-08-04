package naksha.model.objects

import naksha.base.Fnv1a32
import naksha.base.Int64
import naksha.base.Base
import naksha.geo.HereTile
import naksha.base.Action
import naksha.model.IMemberProcessor
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
    companion object XyzProcessor_C {
        /**
         * Ensures that [XyzCreatedAt][naksha.model.objects.XyzMembers.XyzMembers_C.XyzCreatedAt] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzCreatedAt = IMemberProcessor { _, collection, feature, _, value ->
            if (feature is NakshaFeature) {
                when (value) {
                    is Int64 -> value
                    is Number -> Int64(value.toLong())
                    else -> {
                        val action = collection.useMember(StandardMembers.TnMember).readTupleNumber(feature)?.action
                        if (action == Action.CREATE) null else feature.properties.xyz.createdAt
                    }
                }
            } else value
        }

        /**
         * Ensures that [XyzUpdatedAt][naksha.model.objects.XyzMembers.XyzMembers_C.XyzUpdatedAt] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzUpdatedAt = IMemberProcessor { _, _, _, _, _ -> Base.currentMillis() }

        /**
         * Ensures that [XyzAppId][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAppId] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAppId = IMemberProcessor { session, _, _, _, _ -> session.options.appId }

        /**
         * Ensures that [XyzAuthor][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthor] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthor = IMemberProcessor { session, _, _, _, value -> session.options.author ?: value as String? }

        /**
         * Ensures that [XyzAuthorTimestamp][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthorTimestamp] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthorTimestamp = IMemberProcessor { session, _, _, _, value ->
            if (session.options.author != null) Base.currentMillis() else value as Int64?
        }

        /**
         * Computes [XyzHereTile][naksha.model.objects.XyzMembers.XyzMembers_C.XyzHereTile] from the feature's
         * reference-point (or geometry centroid), falling back to the id hash when there is no geometry.
         * @since 3.0
         */
        @JvmStatic
        val xyzHereTile = IMemberProcessor { _, _, feature, _, value ->
            if (feature is NakshaFeature) {
                val point = feature.referencePoint ?: feature.geometry?.calculateCentroid()
                if (point != null) HereTile(point.latitude, point.longitude).intKey else Fnv1a32.string(0, feature.id.text)
            } else value
        }

        /**
         * Computes [XyzHash][naksha.model.objects.XyzMembers.XyzMembers_C.XyzHash] for the feature.
         * @since 3.0
         */
        @JvmStatic
        val xyzHash = IMemberProcessor { _, _, feature, _, value ->
            if (feature is NakshaFeature) Fnv1a32.string(0, feature.id.text) else value
        }
    }
}
