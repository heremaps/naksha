@file:Suppress("OPT_IN_USAGE")

package naksha.model

import kotlin.js.JsExport

/**
 * Creates the configuration of a storage by asking one question after another, until it is [done][isDone].
 * @since 3.0
 * @see IStorageProvider.newConfigBuilder
 */
@JsExport
interface IStorageConfigBuilder {
    /**
     * If all questions are answered, so that [build] can be called.
     * @since 3.0
     */
    val isDone: Boolean

    /**
     * An explanation of the current question, can be empty.
     * @since 3.0
     */
    val description: String

    /**
     * The current question.
     * @since 3.0
     */
    val question: String

    /**
     * The allowed answers, or `null`, if any answer is allowed.
     * @since 3.0
     */
    val options: Array<String>?

    /**
     * The answer to use when nothing is entered, or `null`, if an answer is required.
     * @since 3.0
     */
    val defaultAnswer: String?

    /**
     * If the answer should not be shown while being entered, for example a password.
     * @since 3.0
     */
    val isSecret: Boolean

    /**
     * Answers the current question and moves to the next one.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_ARGUMENT][naksha.base.NakshaError.ILLEGAL_ARGUMENT], if the answer is invalid.
     */
    fun answer(answer: String)

    /**
     * Returns the configuration as JSON.
     * @since 3.0
     * @throws naksha.base.NakshaException with error [ILLEGAL_STATE][naksha.base.NakshaError.ILLEGAL_STATE], if the builder is not [done][isDone].
     */
    fun build(): String
}
