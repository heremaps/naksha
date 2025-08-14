package com.here.naksha.app.init.context;

import com.here.naksha.app.service.NakshaApp;
import com.here.naksha.lib.core.util.IoHelp;
import com.here.naksha.lib.hub.util.ConfigUtil;
import naksha.model.Naksha;
import naksha.model.objects.NakshaStorage;
import naksha.psql.PgConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.here.naksha.app.service.NakshaApp.newInstance;
import static java.util.Objects.requireNonNull;

public class ContainerTestContext extends TestContext {

  private static final Logger log = LoggerFactory.getLogger(ContainerTestContext.class);

  public ContainerTestContext() {
    super(ContainerTestContext::createApp);
  }

  private static NakshaApp createApp() {
    log.info("Warmup docker container for data-db");
    final var dataStorage = requireNonNull(ConfigUtil.readConfigFile("docker-data-config", NakshaStorage.TYPE));
    Naksha.useStorage(dataStorage);
    log.info("Starting NakshaApp using docker container");
    return newInstance("run", "docker-test-config");
  }

  @Override
  void teardownStorage() {
    super.teardownStorage();
  }
}
