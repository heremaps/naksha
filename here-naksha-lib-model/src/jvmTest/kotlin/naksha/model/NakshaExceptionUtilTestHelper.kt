package naksha.model

object NakshaExceptionUtilTestHelper {
    @JvmStatic
    fun throwNakshaException(code: String){
        throw NakshaException(NakshaError(code = code, msg = "test message"))
    }
}