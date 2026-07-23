package com.here.naksha.app.init.context;

import static com.here.naksha.app.service.NakshaApp.newInstance;

import com.here.naksha.app.init.TestStorageConfig;
import com.here.naksha.app.init.TestStorageConfigs;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.request.Response;
import naksha.model.request.SuccessResponse;
import naksha.model.request.Write;
import naksha.model.request.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalTestContext extends TestContext {

  private static final Logger log = LoggerFactory.getLogger(LocalTestContext.class);

  private static final String CONFIG_ID = "test-config";
  private static final TestStorageConfig STORAGE_CONFIG = TestStorageConfigs.dataDbConfig;
  private static final String MASTER_URL = STORAGE_CONFIG.pgConfig().getMasterUri();

  private final NakshaContext nakshaContext;

  public LocalTestContext() {
    super(() -> newInstance(CONFIG_ID, MASTER_URL));
    nakshaContext = NakshaContext.newInstance("local-test")
        .withSu(true)
        .attachToCurrentThread();
  }

  @Override
  void setupStorage() {
    super.setupStorage();
    if (!MASTER_URL.isBlank()) {
      log.info("Removing map (schema) {} for db with url: {}", STORAGE_CONFIG.mapId(), MASTER_URL);
      SessionOptions sessionOptions = SessionOptions.from(nakshaContext, true);
      Response response = Naksha.useStorage(STORAGE_CONFIG.pgConfig()).useWriteSession(sessionOptions,
          writer -> writer.execute(new WriteRequest().add(new Write().deleteCatalog(STORAGE_CONFIG.mapId()))));
      if(response instanceof SuccessResponse){
        log.info("Removed map (which should drop schema of the same name): {}", STORAGE_CONFIG.mapId());
      } else {
        log.warn("Could not remove map: {}, unexpected response: {}", STORAGE_CONFIG.mapId(), response);
      }
    }
  }
}
