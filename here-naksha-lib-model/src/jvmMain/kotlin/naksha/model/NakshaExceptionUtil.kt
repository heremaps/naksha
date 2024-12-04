package naksha.model

import java.lang.RuntimeException

class NakshaExceptionUtil private constructor(){
    companion object NakshaExceptionUtil_C {
        @JvmStatic
        fun isNakshaExceptionWithCode(re: RuntimeException, expectedCode: String): Boolean =
            re is NakshaException && re.error.code == expectedCode
    }
}
