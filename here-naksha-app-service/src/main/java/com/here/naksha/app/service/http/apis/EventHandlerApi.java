package com.here.naksha.app.service.http.apis;

import com.here.naksha.app.service.http.NakshaHttpVerticle;
import com.here.naksha.app.service.http.tasks.EventHandlerApiTask;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.here.naksha.app.service.http.tasks.EventHandlerApiTask.EventHandlerApiReqType.CREATE_HANDLER;

public class EventHandlerApi extends Api{

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerApi.class);

    public EventHandlerApi(@NotNull NakshaHttpVerticle verticle) {
        super(verticle);
    }

    @Override
    public void addOperations(@NotNull RouterBuilder rb) {
        rb.operation("createHandler").handler(this::createEventHandler);
    }

    @Override
    public void addManualRoutes(@NotNull Router router) {

    }

    private void createEventHandler(final @NotNull RoutingContext routingContext) {
        new EventHandlerApiTask<>(
                CREATE_HANDLER,
                verticle,
                naksha(),
                routingContext,
                verticle.createNakshaContext(routingContext))
                .start();
    }
}
