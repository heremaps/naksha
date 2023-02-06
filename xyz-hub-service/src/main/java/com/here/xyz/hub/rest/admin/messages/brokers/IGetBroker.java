package com.here.xyz.hub.rest.admin.messages.brokers;

import com.here.xyz.hub.rest.admin.MessageBroker;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface IGetBroker {
  @NotNull MessageBroker get();
}
