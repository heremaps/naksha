package naksha.model

import naksha.model.request.Request
import naksha.model.request.Response
import java.lang.AutoCloseable

//TODO: refine, maybe delete
object AutoCloseableUtil {

    @JvmStatic
    fun openWriteSession(storage: IStorage, sessionOptions: SessionOptions? = null): AutoCloseable {
        return JvmCloseableWriteSession(storage.newWriteSession(sessionOptions))
    }

    @JvmStatic
    fun openReadSession(storage: IStorage, sessionOptions: SessionOptions?): AutoCloseable {
        return JvmCloseableReadSession(storage.newReadSession(sessionOptions))
    }

    @JvmStatic
    fun <S: ISession, T> useAndClose(session: S, action: (S) -> T): T {
        session as AutoCloseable
        return session.use(action)
    }
}

class JvmCloseableReadSession(val actual: IReadSession): AutoCloseable, IReadSession by actual {
    override fun executeParallel(request: Request): Response {
        return actual.executeParallel(request)
    }
}

class JvmCloseableWriteSession(val actual: IWriteSession): AutoCloseable, IWriteSession by actual {
    override fun executeParallel(request: Request): Response {
        return actual.executeParallel(request)
    }
}
