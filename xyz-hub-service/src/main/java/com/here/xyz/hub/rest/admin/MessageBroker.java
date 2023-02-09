/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.xyz.hub.Service;
import com.here.xyz.hub.rest.AdminApi;
import com.here.xyz.hub.rest.admin.messages.RelayedMessage;
import io.vertx.core.Future;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.ConnectionBase;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The MessageBroker provides the infrastructural implementation of how to send & receive {@link AdminMessage}s.
 */
public interface MessageBroker {

  Logger logger = LogManager.getLogger();
  ThreadLocal<ObjectMapper> mapper = ThreadLocal.withInitial(ObjectMapper::new);

  void sendRawMessage(String jsonMessage);

  default void sendMessage(AdminMessage message) {
    if (!Service.get().node.equals(message.destination)) {
      String jsonMessage = null;
      try {
        if (message instanceof RelayedMessage && ((RelayedMessage) message).globalRelay) {
          RelayedMessage rm = (RelayedMessage) message;
          //Remote message does not need to be globally relayed again, but it needs be done remotely
          rm.globalRelay = false;
          boolean originalRelay = rm.relay;
          rm.relay = true;
          //Send messages to remote cluster async
          jsonMessage = mapper.get().writeValueAsString(message);
          //Re-set the relay value to its original value
          rm.relay = originalRelay;
          sendRawMessagesToRemoteCluster(jsonMessage, 0);
        }

        //Send the local version of the message
        jsonMessage = mapper.get().writeValueAsString(message);
        sendRawMessage(jsonMessage);
      }
      catch (JsonProcessingException e) {
        logger.error("Error while serializing AdminMessage of type {} prior to send it.", message.getClass().getSimpleName());
      }
      catch (Exception e) {
        logger.error("Error while sending AdminMessage: {}", jsonMessage, e);
      }
    }
    //Receive it (also) locally (if applicable)
    /*
    NOTE: Local messages will always be received directly and only once. This is also true for a broadcast message
    with the #broadcastIncludeLocalNode flag being active.
     */
    receiveMessage(message);
  }

  default void sendRawMessagesToRemoteCluster(String jsonMessage, int tryCount) {
    int finalTryCount = tryCount++;

    List<String> hubRemoteUrls = Service.get().config.getHubRemoteServiceUrls();
    if (hubRemoteUrls != null && !hubRemoteUrls.isEmpty()) {
      for (String remoteUrl : hubRemoteUrls) {
        if (remoteUrl.isEmpty()) continue;
        try {
          Service.get().webClient
              .postAbs(remoteUrl + AdminApi.ADMIN_MESSAGES_ENDPOINT)
              .timeout(29_000)
              .putHeader("content-type", "application/json; charset=" + Charset.defaultCharset().name())
              .putHeader("Authorization", "Bearer " + Service.get().config.ADMIN_MESSAGE_JWT)
              .sendBuffer(Buffer.buffer(jsonMessage), ar -> {
                if (ar.failed()) {
                  if (ar.cause() == ConnectionBase.CLOSED_EXCEPTION && finalTryCount <= 1) {
                    logger.warn("Closed connection. Retrying to sent message to remote cluster. URLs:" + remoteUrl);
                    sendRawMessagesToRemoteCluster(jsonMessage, finalTryCount);
                  } else {
                    logger.error("Failed to sent message to remote cluster. URLs: " + remoteUrl,
                        ar.cause());
                  }
                }
              });
        }
        catch (Exception e) {
          logger.error("Failed to sent message to remote cluster. URLs: " + remoteUrl,
                  e.getCause());
        }
      }
    }
  }

  default void receiveRawMessage(byte[] rawJsonMessage) {
    if (rawJsonMessage == null) {
      logger.error("No bytes given for receiving the message.", new NullPointerException());
      return;
    }
    receiveRawMessage(new String(rawJsonMessage));
  }

  default void receiveRawMessage(String jsonMessage) {
    receiveMessage(deserializeMessage(jsonMessage));
  }

  default AdminMessage deserializeMessage(String jsonMessage) {
    AdminMessage message = null;
    try {
      message = mapper.get().readValue(jsonMessage, AdminMessage.class);
    }
    catch (IOException e) {
      logger.error("Error while de-serializing AdminMessage {} : {}", jsonMessage, e);
    }
    catch (Exception e) {
      logger.error("Error while receiving AdminMessage {} : {}", jsonMessage, e);
    }
    return message;
  }

  default void receiveMessage(AdminMessage message) {
    if (message == null)
      return;
    if (message.source == null)
      throw new NullPointerException("The source node of the AdminMessage must be defined.");

    if (message.destination == null && (!Service.get().node.equals(message.source) || message.broadcastIncludeLocalNode)
        || Service.get().node.equals(message.destination)) {
      try {
        message.handle();
      }
      catch (RuntimeException e) {
        logger.error("Error while trying to handle AdminMessage {} : {}", message, e);
      }
    }
  }

  /**
   * Asynchronously detect the amount of subscribers to admin messages. This is the node number.
   * @return the future of the result.
   */
  @Nonnull
  Future<Integer> fetchSubscriberCount();
}
