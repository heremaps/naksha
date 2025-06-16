package naksha.mom.v2

import naksha.base.JsEnum
import naksha.base.Platform.Platform_C.forKClass
import naksha.base.PlatformType
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField

/**
 * Enumeration for possible review states.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class MomReviewState : JsEnum() {
    override fun namespace() = TYPE
    override fun initClass() {}

    companion object ReviewStateEnum_C {
        /**
         * The [PlatformType] of [MomReviewState].
         * @since 3.0
         */
        @JvmField
        @JsStatic
        val TYPE = forKClass(MomReviewState::class).withPackageName(PACKAGE_NAME)

        /**
         * This is the initial state for any un-moderated feature. The default for all new features.
         */
        @JvmField
        @JsStatic
        val UNPUBLISHED = defIgnoreCase(TYPE, "UNPUBLISHED")

        /**
         * Set by the auto-endorser, if the feature is ready to be sent into the bucket-processor.
         */
        @JvmField
        @JsStatic
        val AUTO_ENDORSED = defIgnoreCase(TYPE, "AUTO_ENDORSED")

        /**
         * Set by the auto-endorser, if the change should be reverted.
         */
        @JvmField
        @JsStatic
        val AUTO_ROLLBACK = defIgnoreCase(TYPE, "AUTO_ROLLBACK") { self -> self.isFinalState = true }

        /**
         * Set by the auto-endorser, if the feature must be reviewed by a moderator.
         */
        @JvmField
        @JsStatic
        val AUTO_REVIEW_DEFERRED = defIgnoreCase(TYPE, "AUTO_REVIEW_DEFERRED")

        /**
         * Set by the change-set-publisher, if the feature was integrated into consistent-store.
         */
        @JvmField
        @JsStatic
        val AUTO_INTEGRATED = defIgnoreCase(TYPE, "AUTO_INTEGRATED") { self -> self.isFinalState = true }

        /**
         * Set by the change-set-publisher, if the feature integration failed and more moderation is needed.
         */
        @JvmField
        @JsStatic
        val FAILED = defIgnoreCase(TYPE, "FAILED")

        /**
         * Set by a moderator, when the feature is ready to be send to the bucket-processor.
         */
        @JvmField
        @JsStatic
        val ENDORSED = defIgnoreCase(TYPE, "ENDORSED")

        /**
         * Set by a moderator, when the feature need more moderation.
         */
        @JvmField
        @JsStatic
        val UNDECIDED = defIgnoreCase(TYPE, "UNDECIDED")

        /**
         * Set by a moderator, when the feature is rejected, the change should be reverted.
         */
        @JvmField
        @JsStatic
        val ROLLBACK = defIgnoreCase(TYPE, "ROLLBACK") { self -> self.isFinalState = true }

        /**
         * Set by a moderator, when the feature was manually coded into RMOB. In-between state, that eventually will be changed into
         * [.AUTO_INTEGRATED].
         */
        @JvmField
        @JsStatic
        val INTEGRATED = defIgnoreCase(TYPE, "INTEGRATED")

        fun of(value: String): MomReviewState = get(value, TYPE)
    }

    /**
     * If this is a final state.
     */
    var isFinalState: Boolean = false
        private set
}