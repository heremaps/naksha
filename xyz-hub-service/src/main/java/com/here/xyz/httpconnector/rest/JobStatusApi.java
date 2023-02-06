package com.here.xyz.httpconnector.rest;

import com.here.xyz.httpconnector.config.JDBCImporter;
import com.here.xyz.httpconnector.util.status.RDSStatus;
import com.here.xyz.hub.Service;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.here.xyz.hub.rest.Api.HeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

public class JobStatusApi {

  private static final Logger logger = LogManager.getLogger();
  private final String JOB_QUEUE_STATUS_ENDPOINT = "/psql/system/status";
  private final JSONObject system;

  public JobStatusApi(Router router) {
    this.system = new JSONObject();

    /** Add static configurations */
    this.system.put("MAX_RDS_INFLIGHT_IMPORT_BYTES", Service.get().config.JOB_MAX_RDS_INFLIGHT_IMPORT_BYTES);
    this.system.put("MAX_RDS_CAPACITY", Service.get().config.JOB_MAX_RDS_CAPACITY);
    this.system.put("MAX_RDS_CPU_LOAD", Service.get().config.JOB_MAX_RDS_CPU_LOAD);
    this.system.put("MAX_RUNNING_JOBS", Service.get().config.JOB_MAX_RUNNING_JOBS);

    this.system.put("SUPPORTED_CONNECTORS", Service.get().supportedConnectors);
    this.system.put("JOB_QUEUE_INTERVAL", Service.get().config.JOB_CHECK_QUEUE_INTERVAL_SECONDS);
    this.system.put("JOB_DYNAMO_EXP_IN_DAYS", Service.get().config.JOB_DYNAMO_EXP_IN_DAYS);
    this.system.put("HOST_ID", Service.get().node.id);

    router.route(HttpMethod.GET, JOB_QUEUE_STATUS_ENDPOINT)
        .handler(this::getSystemStatus);
  }

  private void getSystemStatus(final RoutingContext context) {

    HttpServerResponse httpResponse = context.response().setStatusCode(OK.code());
    httpResponse.putHeader(CONTENT_TYPE, APPLICATION_JSON);

    JSONObject status = new JSONObject();
    status.put("SYSTEM", this.system);
    status.put("RUNNING_JOBS", Service.get().importQueue.getQueue());

    List<Future> statusFutures = new ArrayList<>();
    JDBCImporter.getClientList().forEach(
        clientId -> {
          if (Service.get().supportedConnectors.contains(clientId)) {
            statusFutures.add(JDBCImporter.getRDSStatus(clientId));
          }
        }
    );

    CompositeFuture.join(statusFutures)
        .onComplete(f -> {
          JSONObject rdsStatusList = new JSONObject();

          statusFutures.forEach(f1 -> {
            if (f1.succeeded()) {
              RDSStatus rdsStatus = (RDSStatus) f1.result();
              rdsStatusList.put(rdsStatus.getClientId(), new JSONObject(Json.encode(rdsStatus)));
            }
          });
          status.put("RDS", rdsStatusList);

          httpResponse.end(status.toString());

        })
        .onFailure(f -> httpResponse.end(status.toString()));
  }
}
