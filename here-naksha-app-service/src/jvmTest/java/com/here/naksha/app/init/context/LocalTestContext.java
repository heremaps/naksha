package com.here.naksha.app.init.context;

import com.here.naksha.app.init.TestStorageConfig;
import com.here.naksha.app.init.TestStorageConfigs;
import com.here.naksha.app.service.NakshaApp;
import naksha.model.NakshaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalTestContext extends TestContext {

  private static final Logger log = LoggerFactory.getLogger(LocalTestContext.class);

  private static final String CONFIG_ID = "test-config";
  private static final TestStorageConfig ADMIN_STORAGE = TestStorageConfigs.adminDbConfig;
  private static final TestStorageConfig DATA_STORAGE = TestStorageConfigs.dataDbConfig;

  private final NakshaContext nakshaContext;

  public LocalTestContext() {
    super(() -> NakshaApp.newInstance("run", "localhost-test-config") );
    nakshaContext = NakshaContext.newInstance("local-test") .withSu(true).attachToCurrentThread();
  }

  @Override
  void setupStorage() {
    super.setupStorage();
//    if (!MASTER_URL.isBlank()) {
//      log.info("Removing map (schema) {} for db with url: {}", DATA_STORAGE.mapId(), DATA_STORAGE.);
//      SessionOptions sessionOptions = SessionOptions.from(nakshaContext, true);
//      Response response = Naksha.useStorage(DATA_STORAGE.config()).useWriteSession(sessionOptions,
//          writer -> writer.execute(new WriteRequest().add(new Write().deleteMapById(DATA_STORAGE.mapId()))));
//      if(response instanceof SuccessResponse){
//        log.info("Removed map (which should drop schema of the same name): {}", DATA_STORAGE.mapId());
//      } else {
//        log.warn("Could not remove map: {}, unexpected response: {}", DATA_STORAGE.mapId(), response);
//      }
//    }
  }
}