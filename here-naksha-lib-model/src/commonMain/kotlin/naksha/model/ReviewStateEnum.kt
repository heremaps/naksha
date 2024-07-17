package naksha.model

import naksha.base.JsEnum
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.reflect.KClass

/**
 * Enumeration for possible review states.
 */
@Suppress("OPT_IN_USAGE")
@JsExport
class ReviewStateEnum : JsEnum() {
    @Suppress("NON_EXPORTABLE_TYPE")
    override fun namespace(): KClass<out JsEnum> = ReviewStateEnum::class

    override fun initClass() {}

    companion object ReviewStateEnumCompanion {

        /**
         * This is the initial state for any un-moderated feature. The default for all new features.
         */
        @JvmField
        val UNPUBLISHED = defIgnoreCase(ReviewStateEnum::class, "UNPUBLISHED")

        /**
         * Set by the auto-endorser, if the feature is ready to be sent into the bucket-processor.
         */
        @JvmField
        val AUTO_ENDORSED = defIgnoreCase(ReviewStateEnum::class, "AUTO_ENDORSED")

        /**
         * Set by the auto-endorser, if the change should be reverted.
         */
        @JvmField
        val AUTO_ROLLBACK = defIgnoreCase(ReviewStateEnum::class, "AUTO_ROLLBACK") {
            isFinalState = true
        }
        /**
         * Set by the auto-endorser, if the feature must be reviewed by a moderator.
         */
        @JvmField
        val AUTO_REVIEW_DEFERRED = defIgnoreCase(ReviewStateEnum::class, "AUTO_REVIEW_DEFERRED")

        /**
         * Set by the change-set-publisher, if the feature was integrated into consistent-store.
         */
        @JvmField
        val AUTO_INTEGRATED = defIgnoreCase(ReviewStateEnum::class, "AUTO_INTEGRATED") {
            isFinalState = true
        }

        /**
         * Set by the change-set-publisher, if the feature integration failed and more moderation is needed.
         */
        @JvmField
        val FAILED = defIgnoreCase(ReviewStateEnum::class, "FAILED")

        /**
         * Set by a moderator, when the feature is ready to be send to the bucket-processor.
         */
        @JvmField
        val ENDORSED = defIgnoreCase(ReviewStateEnum::class, "ENDORSED")

        /**
         * Set by a moderator, when the feature need more moderation.
         */
        @JvmField
        val UNDECIDED = defIgnoreCase(ReviewStateEnum::class, "UNDECIDED")

        /**
         * Set by a moderator, when the feature is rejected, the change should be reverted.
         */
        @JvmField
        val ROLLBACK = defIgnoreCase(ReviewStateEnum::class, "ROLLBACK") {
            isFinalState = true
        }

        /**
         * Set by a moderator, when the feature was manually coded into RMOB. In-between state, that eventually will be changed into
         * [.AUTO_INTEGRATED].
         */
        @JvmField
        val INTEGRATED = defIgnoreCase(ReviewStateEnum::class, "INTEGRATED")

        /**
         * If this is a final state.
         */
        var isFinalState = false
            private set

    }
}