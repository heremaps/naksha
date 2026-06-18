package naksha.model.objects

import naksha.base.Int64
import naksha.base.Platform
import naksha.model.IMemberProcessor
import naksha.model.ISession
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
        val xyzCreatedAt = fun(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): Int64 {
            if (value is Int64) return value
            if (value is Number) return Int64(value.toLong())
            return Platform.currentMillis()
        } as IMemberProcessor

        /**
         * Ensures that [XyzUpdatedAt][naksha.model.objects.XyzMembers.XyzMembers_C.XyzUpdatedAt] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzUpdatedAt = fun(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): Int64 {
            return Platform.currentMillis()
        } as IMemberProcessor

        /**
         * Ensures that [XyzAppId][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAppId] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAppId = fun(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): String {
            return session.options.appId
        } as IMemberProcessor

        /**
         * Ensures that [XyzAuthor][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthor] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthor = fun(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): String? {
            val author = session.options.author
            return author ?: value as String?
        } as IMemberProcessor

        /**
         * Ensures that [XyzAuthorTimestamp][naksha.model.objects.XyzMembers.XyzMembers_C.XyzAuthorTimestamp] is set correctly.
         * @since 3.0
         */
        @JvmStatic
        val xyzAuthorTimestamp = fun(session: ISession, collection: NakshaCollection, feature: NakshaFeature, member: Member, value: Any?): Int64? {
            val author = session.options.author
            return if (author != null) Platform.currentMillis() else value as Int64?
        } as IMemberProcessor
    }
}