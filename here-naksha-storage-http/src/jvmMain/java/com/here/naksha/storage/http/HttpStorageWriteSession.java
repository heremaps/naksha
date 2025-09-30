package com.here.naksha.storage.http;

import com.here.naksha.storage.http.connector.ConnectorInterfaceWriteExecute;
import naksha.base.AtomicInt;
import naksha.model.ILock;
import naksha.model.IWriteSession;
import naksha.model.NakshaContext;
import naksha.model.NakshaError;
import naksha.model.objects.NakshaTx;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Request;
import naksha.model.request.Response;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpStorageWriteSession extends HttpStorageReadSession implements IWriteSession {

    private static final Logger log = LoggerFactory.getLogger(HttpStorageWriteSession.class);

    private final HttpInterface httpInterface;

    public HttpStorageWriteSession(NakshaContext context, RequestSender requestSender, HttpInterface httpInterface) {
        super(context, requestSender, httpInterface);
        this.httpInterface = httpInterface;
    }

    @Override
    public @NotNull Response execute(@NotNull Request writeRequest) {
        try {
            return switch (httpInterface) {
                case ffwAdapter -> new ErrorResponse(
                        NakshaError.NOT_IMPLEMENTED, "Writing not supported by underlying storage");
                case dataHubConnector -> new ConnectorInterfaceWriteExecute(
                        getNakshaContext(), (WriteRequest) writeRequest, getRequestSender())
                        .execute();
            };
        } catch (ConnectorInterfaceWriteExecute.ConflictException e) {
            return new ErrorResponse(NakshaError.CONFLICT, e.getMessage(), e);
        } catch (UnsupportedOperationException e) {
            return new ErrorResponse(NakshaError.NOT_IMPLEMENTED, e.getMessage(), e);
        } catch (Exception e) {
            log.warn("We got exception while executing Write request.", e);
            return new ErrorResponse(NakshaError.EXCEPTION, e.getMessage(), e);
        }
    }

    @Override
    public @NotNull AtomicInt getUid() {
        return null;
    }

    @Override
    public @NotNull ILock acquireSessionLock(@NotNull String lockId) {
        return null;
    }

    @Override
    public @NotNull ILock acquireTransactionLock(@NotNull String lockId) {
        return null;
    }

    @Override
    public void commit() {
        // do nothing
    }

    @Override
    public void rollback() {
        // do nothing
    }

    @Override
    public @NotNull NakshaTx useTransaction() {
        return null;
    }

    @Override
    public @Nullable NakshaTx getTransaction() {
        return null;
    }
}
