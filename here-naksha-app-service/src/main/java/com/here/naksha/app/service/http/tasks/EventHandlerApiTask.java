package com.here.naksha.app.service.http.tasks;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.NakshaAdminCollection;
import com.here.naksha.lib.core.NakshaContext;
import com.here.naksha.lib.core.models.XyzError;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.payload.XyzResponse;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.models.storage.WriteFeatures;
import com.here.naksha.lib.core.storage.IWriteSession;
import com.here.naksha.lib.core.util.json.Json;
import com.here.naksha.lib.core.util.storage.RequestHelper;
import com.here.naksha.lib.core.view.ViewDeserialize;
import io.vertx.ext.web.RoutingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHandlerApiTask<T extends XyzResponse> extends AbstractApiTask<XyzResponse> {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerApiTask.class);

    private final @NotNull EventHandlerApiReqType reqType;

    public EventHandlerApiTask(
            final @NotNull EventHandlerApiReqType reqType,
            final @NotNull NakshaHttpVerticle verticle,
            final @NotNull INaksha nakshaHub,
            final @NotNull RoutingContext routingContext,
            final @NotNull NakshaContext nakshaContext) {
        super(verticle, nakshaHub, routingContext, nakshaContext);
        this.reqType = reqType;
    }

    public enum EventHandlerApiReqType {
        GET_ALL_HANDLERS,
        GET_HANDLER_BY_ID,
        CREATE_HANDLER,
        UPDATE_HANDLER,
        DELETE_HANDLER
    }

    @Override
    protected void init() {}

    @Override
    protected @NotNull XyzResponse execute() {
        try {
            return switch (reqType) {
                case CREATE_HANDLER -> executeCreateHandler();
                default -> executeUnsupported();
            };
        } catch (Exception ex) {
            // unexpected exception
            return verticle.sendErrorResponse(
                    routingContext, XyzError.EXCEPTION, "Internal error : " + ex.getMessage());
        }
    }

    private @NotNull XyzResponse executeCreateHandler() throws Exception {
        // Read request JSON
        EventHandler newHandler = null;
        try (final Json json = Json.get()) {
            final String bodyJson = routingContext.body().asString();
            newHandler = json.reader(ViewDeserialize.User.class)
                    .forType(EventHandler.class)
                    .readValue(bodyJson);
        }
        // persist new handler in Admin DB (if doesn't exist already)
        try (final IWriteSession writeSession = naksha().getSpaceStorage().newWriteSession(context(), true)) {
            final WriteFeatures<EventHandler> writeRequest =
                    RequestHelper.createFeatureRequest(NakshaAdminCollection.EVENT_HANDLERS,newHandler,false);
            final Result writeResult = writeSession.execute(writeRequest);
            return transformWriteResultToXyzFeatureResponse(writeResult, EventHandler.class);
        }
    }
}
