package naksha.model.mom

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * Enumeration for possible review states.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class MomReviewState : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = MomReviewState::class

    override fun initClass() {}

    companion object ReviewStateEnumCompanion {

        /**
         * This is the initial state for any un-moderated feature. The default for all new features.
         */
        @JvmField
        @JsStatic
        val UNPUBLISHED = defIgnoreCase(MomReviewState::class, "UNPUBLISHED")

        /**
         * Set by the auto-endorser, if the feature is ready to be sent into the bucket-processor.
         */
        @JvmField
        @JsStatic
        val AUTO_ENDORSED = defIgnoreCase(MomReviewState::class, "AUTO_ENDORSED")

        /**
         * Set by the auto-endorser, if the change should be reverted.
         */
        @JvmField
        @JsStatic
        val AUTO_ROLLBACK = defIgnoreCase(MomReviewState::class, "AUTO_ROLLBACK") { self -> self.isFinalState = true }

        /**
         * Set by the auto-endorser, if the feature must be reviewed by a moderator.
         */
        @JvmField
        @JsStatic
        val AUTO_REVIEW_DEFERRED = defIgnoreCase(MomReviewState::class, "AUTO_REVIEW_DEFERRED")

        /**
         * Set by the change-set-publisher, if the feature was integrated into consistent-store.
         */
        @JvmField
        @JsStatic
        val AUTO_INTEGRATED =
            defIgnoreCase(MomReviewState::class, "AUTO_INTEGRATED") { self -> self.isFinalState = true }

        /**
         * Set by the change-set-publisher, if the feature integration failed and more moderation is needed.
         */
        @JvmField
        @JsStatic
        val FAILED = defIgnoreCase(MomReviewState::class, "FAILED")

        /**
         * Set by a moderator, when the feature is ready to be send to the bucket-processor.
         */
        @JvmField
        @JsStatic
        val ENDORSED = defIgnoreCase(MomReviewState::class, "ENDORSED")

        /**
         * Set by a moderator, when the feature need more moderation.
         */
        @JvmField
        @JsStatic
        val UNDECIDED = defIgnoreCase(MomReviewState::class, "UNDECIDED")

        /**
         * Set by a moderator, when the feature is rejected, the change should be reverted.
         */
        @JvmField
        @JsStatic
        val ROLLBACK = defIgnoreCase(MomReviewState::class, "ROLLBACK") { self -> self.isFinalState = true }

        /**
         * Set by a moderator, when the feature was manually coded into RMOB. In-between state, that eventually will be changed into
         * [.AUTO_INTEGRATED].
         */
        @JvmField
        @JsStatic
        val INTEGRATED = defIgnoreCase(MomReviewState::class, "INTEGRATED")

        fun of(value: String): MomReviewState = get(value, MomReviewState::class)
    }

    /**
     * If this is a final state.
     */
    var isFinalState: Boolean = false
        private set
}