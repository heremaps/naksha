@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package naksha.base

import naksha.base.Platform.PlatformCompanion.forKClass

actual class NakshaException actual constructor(actual val error: NakshaError) : RuntimeException(error.msg, error.cause) {
    actual companion object NakshaExceptionCompanion {
        /**
         * The [PlatformType] of [NakshaException].
         * @since 3.0
         */
        @JvmField
        actual val TYPE: PlatformType<NakshaException> = forKClass(NakshaException::class).withPackageName(PACKAGE_NAME)
    }
    actual constructor(code: String, msg: String) : this(NakshaError(code, msg))
    actual constructor(code: String, msg: String, streamId: String) : this(NakshaError(code, msg, streamId = streamId))
    actual constructor(code: String, msg: String, cause: Throwable) : this(NakshaError(code, msg, cause))
    actual constructor(code: String, msg: String, cause: Throwable?, streamId: String?) : this(NakshaError(code, msg, cause, streamId))
}