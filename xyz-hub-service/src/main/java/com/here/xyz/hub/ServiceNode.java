/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package com.here.xyz.hub;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.here.xyz.httpconnector.CService;
import com.here.xyz.hub.rest.admin.AdminMessage;
import com.here.xyz.hub.rest.health.HealthApi;
import com.here.xyz.hub.util.health.Config;
import com.here.xyz.hub.util.health.schema.Response;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.ext.web.client.HttpResponse;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Node represents one running Service Node of the XYZ Hub Service.
 */
public class ServiceNode {

  private static final int HEALTH_TIMEOUT = 25;
  private static final Logger logger = LogManager.getLogger();

  private int nodeCount;
  private static final int NODE_COUNT_FETCH_PERIOD = 30_000; //ms
  private static final int CLUSTER_NODES_CHECKER_PERIOD = 120_000; //ms
  private static final int CLUSTER_NODES_PING_PERIOD = 600_000; //ms
  private static final int MAX_HC_FAILURE_COUNT = 3;

  private static final Set<ServiceNode> otherClusterNodes = new CopyOnWriteArraySet<>();
  private static final String UNKNOWN_ID = "UNKNOWN";

  @JsonProperty
  public String id;

  @JsonProperty
  public String ip;

  @JsonProperty
  public int port;

  @JsonIgnore
  public int consecutiveHcFailures;

  @JsonIgnore
  private URL url;

  @JsonIgnore
  private final @Nullable Service service;

  public ServiceNode(
      @JsonProperty("id") String id,
      @JsonProperty("ip") String ip,
      @JsonProperty("port") int port
  ) {
    this(null, id, ip, port);
  }

  ServiceNode(@Nullable Service service, String id, String ip, int port) {
    this.service = service;
    this.id = id;
    this.ip = ip;
    this.port = port;
    nodeCount = Service.get().config.INSTANCE_COUNT;
  }

  void start() {
    if (service != null) {
      nodeCount = service.config.INSTANCE_COUNT;
      startNodeInfoBroadcast();
      initNodeCountFetcher();
      initNodeChecker();
    }
  }

  private void startNodeInfoBroadcast() {
    new NodeInfoNotification().broadcast();
    if (service!= null) Service.get().vertx.setPeriodic(CLUSTER_NODES_PING_PERIOD, timerId -> new NodeInfoNotification().broadcast());
  }

  private void initNodeCountFetcher() {
    if (service != null) Service.get().vertx.setPeriodic(NODE_COUNT_FETCH_PERIOD, this::periodicFetchCountHandler);
  }

  private void periodicFetchCountHandler(@Nonnull Long timerId) {
    final Future<Integer> f = Service.get().messageBroker.fetchSubscriberCount();
    f.onSuccess(this::updateSubscriberCount);
    f.onFailure(this::failedSubscriberCount);
  }

  private void failedSubscriberCount(@Nonnull Throwable cause) {
    logger.warn("Checking service node-count failed.", cause);
  }

  private void updateSubscriberCount(@Nonnull Integer count) {
    nodeCount = count;
    logger.debug("Service node-count: " + nodeCount);
  }

  private void initNodeChecker() {
    if (service != null) {
      Service.get().vertx.setPeriodic(CLUSTER_NODES_CHECKER_PERIOD, timerId -> otherClusterNodes.forEach(otherNode -> otherNode.isHealthy(ar -> {
        if (ar.failed()) {
          otherNode.consecutiveHcFailures++;
          if (otherNode.consecutiveHcFailures >= MAX_HC_FAILURE_COUNT)
            otherClusterNodes.remove(otherNode);
        }
        else
          otherNode.consecutiveHcFailures = 0;
      })));
    }
  }

  public static ServiceNode forIpAndPort(String ip, int port) {
    return new ServiceNode(UNKNOWN_ID, ip, port);
  }

  public void isAlive(Handler<AsyncResult<Void>> callback) {
    callHealthCheck(true, callback);
  }

  public void isHealthy(Handler<AsyncResult<Void>> callback) {
    callHealthCheck(false, callback);
  }

  private void callHealthCheck(boolean onlyAliveCheck, Handler<AsyncResult<Void>> callback) {
    try {
      Service.get().webClient.get(port, ip, HealthApi.MAIN_HEALTCHECK_ENDPOINT)
          .timeout(TimeUnit.SECONDS.toMillis(HEALTH_TIMEOUT))
          .putHeader(Config.getHealthCheckHeaderName(), Config.getHealthCheckHeaderValue())
          .send(ar -> {
            if (ar.succeeded()) {
              HttpResponse<Buffer> response = ar.result();
              if (onlyAliveCheck || response.statusCode() == 200) {
                Response r = response.bodyAsJson(Response.class);
                if (id.equals(r.getNode()))
                  callback.handle(Future.succeededFuture());
                else
                  callback.handle(Future.failedFuture("Node with ID " + id + " and IP " + ip + " is not existing anymore. "
                      + "IP is now used by node with ID " + r.getNode()));
              }
              else {
                callback.handle(Future.failedFuture("Node with ID " + id + " and IP " + ip + " is not healthy."));
              }
            }
            else {
              callback.handle(Future.failedFuture("Node with ID " + id + " and IP " + ip + " is not reachable."));
            }
          });
    }
    catch (final RuntimeException e) {
      if (e == ConnectionBase.CLOSED_EXCEPTION) {
        final RuntimeException re = new RuntimeException("Connection was already closed.", e);
        logger.warn("Error calling health-check of other service node.", re);
        callback.handle(Future.failedFuture(re));
      } else {
        callback.handle(Future.failedFuture(e));
      }
    }
  }

  @JsonIgnore
  public URL getUrl() {
    try {
      url = new URL("http", ip, port, "");
    }
    catch (MalformedURLException e) {
      logger.error("Unable to create the URL for the node with id " + id + ".", e);
    }
    return url;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ServiceNode node = (ServiceNode) o;
    return !UNKNOWN_ID.equals(node.id) && id.equals(node.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  public int count() {
    return Math.max(nodeCount, 1);
  }

  public Set<ServiceNode> getClusterNodes() {
    Set<ServiceNode> clusterNodes = new HashSet<>(otherClusterNodes);
    clusterNodes.add(this);
    return clusterNodes;
  }

  private static class NodeInfoNotification extends AdminMessage {

    public boolean isResponse = false;

    @Override
    protected void handle() {
      otherClusterNodes.add(source);
      if (!isResponse) {
        NodeInfoNotification response = new NodeInfoNotification();
        response.isResponse = true;
        //Send a notification back to the sender
        response.send(source);
      }
    }
  }
}
