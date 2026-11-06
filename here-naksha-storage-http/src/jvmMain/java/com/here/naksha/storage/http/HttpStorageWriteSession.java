package com.here.naksha.storage.http;

import com.here.naksha.lib.circuitbreaker.CircuitBreakerOpenException;
import com.here.naksha.lib.circuitbreaker.ICircuitBreaker;
import com.here.naksha.lib.circuitbreaker.models.CircuitBreakerProps;
import com.here.naksha.storage.http.connector.ConnectorInterfaceWriteExecute;
import naksha.model.ILock;
import naksha.model.IWriteSession;
import naksha.model.MemberProcessorMap;
import naksha.model.NakshaContext;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.objects.NakshaTx;
import naksha.model.request.ErrorResponse;
import naksha.model.request.Response;
import naksha.model.request.WriteRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpStorageWriteSession extends HttpStorageReadSession implements IWriteSession {

    private static final Logger log = LoggerFactory.getLogger(HttpStorageWriteSession.class);

    private final HttpInterface httpInterface;

    public HttpStorageWriteSession(
            @Nullable NakshaContext context,
            @NotNull HttpStorage storage,
            @NotNull RequestSender requestSender,
            @NotNull HttpInterface httpInterface,
            @Nullable ICircuitBreaker circuitBreaker,
            @NotNull String storageId,
            @Nullable CircuitBreakerProps circuitBreakerConfig) {
        super(context, storage, requestSender, httpInterface, circuitBreaker, storageId, circuitBreakerConfig);
        this.httpInterface = httpInterface;
    }

    @Override
    public @NotNull Response executeWrite(@NotNull WriteRequest request) {
        try {
            return executeWithOptionalCircuitBreaker(() -> executeWriteRequest(request));
        } catch (CircuitBreakerOpenException e) {
            log.warn(
                    "Circuit breaker is OPEN or HALF_OPEN (no permits) for storageId: {}. Request rejected.",
                    storageId,
                    e);
            return new ErrorResponse(NakshaError.EXCEPTION, "Service temporarily unavailable", e);
        } catch (NakshaException e) {
            log.info("Unexpected error while executing write", e);
            return new ErrorResponse(e.getError());
        } catch (UnsupportedOperationException e) {
            return new ErrorResponse(NakshaError.NOT_IMPLEMENTED, e.getMessage(), e);
        } catch (Exception e) {
            log.warn("We got exception while executing Write request.", e);
            return new ErrorResponse(NakshaError.EXCEPTION, e.getMessage(), e);
        }
    }

    private @NotNull Response executeWriteRequest(@NotNull WriteRequest request) {
        return switch (httpInterface) {
            case ffwAdapter ->
                    new ErrorResponse(NakshaError.NOT_IMPLEMENTED, "Writing not supported by underlying storage");
            case dataHubConnector ->
                    new ConnectorInterfaceWriteExecute(getNakshaContext(), request, getRequestSender()).execute();
        };
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

    private final @NotNull MemberProcessorMap processors = new MemberProcessorMap();

    @Override
    public @NotNull MemberProcessorMap getProcessors() {
        return processors;
    }
}
