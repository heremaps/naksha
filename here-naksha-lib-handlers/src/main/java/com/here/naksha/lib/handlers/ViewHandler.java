package com.here.naksha.lib.handlers;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandler;
import com.here.naksha.lib.core.models.naksha.EventHandlerProperties;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.core.models.storage.Result;
import com.here.naksha.lib.core.util.json.JsonSerializable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewHandler extends AbstractEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(ViewHandler.class);

    private @NotNull EventHandler eventHandler;
    private @NotNull EventTarget<?> eventTarget;
    private @NotNull EventHandlerProperties properties;

    public ViewHandler(
            final @NotNull EventHandler eventHandler,
            final @NotNull INaksha hub,
            final @NotNull EventTarget<?> eventTarget) {
        super(hub);
        this.eventHandler = eventHandler;
        this.eventTarget = eventTarget;
        this.properties = JsonSerializable.convert(eventHandler.getProperties(), EventHandlerProperties.class);
    }

    @Override
    public @NotNull Result processEvent(@NotNull IEvent event) {

        return event.sendUpstream();
    }
}
