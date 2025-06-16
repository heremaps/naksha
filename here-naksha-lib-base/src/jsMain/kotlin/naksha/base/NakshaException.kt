@file:OptIn(ExperimentalJsExport::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "OPT_IN_USAGE")

package naksha.base

import naksha.base.Platform.Platform_C.forKClass

@JsExport
actual class NakshaException actual constructor(actual val error: NakshaError) : RuntimeException(error.msg, error.cause) {
    actual companion object NakshaException_C {
        /**
         * The [PlatformType] of [NakshaException].
         * @since 3.0
         */
        @JsStatic
        actual val TYPE = forKClass(NakshaException::class).withPackageName(PACKAGE_NAME)
    }

    @JsName("of")
    actual constructor(code: String, msg: String) : this(NakshaError(code, msg))
    @JsName("ofStream")
    actual constructor(code: String, msg: String, streamId: String) : this(NakshaError(code, msg, streamId = streamId))
    @JsName("ofException")
    actual constructor(code: String, msg: String, cause: Throwable) : this(NakshaError(code, msg, cause))
    @JsName("ofStreamException")
    actual constructor(code: String, msg: String, cause: Throwable?, streamId: String?) : this(NakshaError(code, msg, cause, streamId))

}