package com.here.naksha.app.service;

import com.here.naksha.lib.core.IEvent;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.naksha.EventHandlerConfig;
import com.here.naksha.lib.core.models.naksha.EventTarget;
import com.here.naksha.lib.handlers.AbstractEventHandler;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import naksha.model.request.ReadFeatures;
import naksha.model.request.Response;
import org.jetbrains.annotations.NotNull;

/**
 * Test-only deterministic interleaving after a real view read returns, before PATCH can write its captured snapshot. Does not mock or replace storage results.
 */
public class PatchSnapshotRaceHandler extends AbstractEventHandler {
  static final AtomicReference<Consumer<Response>> AFTER_READ = new AtomicReference<>();

  public PatchSnapshotRaceHandler(EventHandlerConfig config, INaksha hub, EventTarget<?> target) {
    super(hub);
  }

  @Override
  protected EventProcessingStrategy processingStrategyFor(IEvent event) {
    if (event.getRequest() instanceof ReadFeatures) return EventProcessingStrategy.PROCESS;
    return EventProcessingStrategy.SEND_UPSTREAM_WITHOUT_PROCESSING;
  }

  @Override
  public @NotNull Response process(@NotNull IEvent event) {
    Response snapshot = event.sendUpstream(event.getRequest());
    Consumer<Response> hook = AFTER_READ.get();
    if (hook != null) hook.accept(snapshot);
    return snapshot;
  }
}
