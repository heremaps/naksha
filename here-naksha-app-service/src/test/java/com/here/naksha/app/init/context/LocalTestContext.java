package com.here.naksha.app.init.context;

import static com.here.naksha.app.service.NakshaApp.newInstance;

import com.here.naksha.app.init.TestStorageConfig;
import com.here.naksha.app.init.TestStorageConfigs;
import naksha.model.Naksha;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.psql.PgSession;
import naksha.psql.PsqlStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalTestContext extends TestContext {

  private static final Logger log = LoggerFactory.getLogger(LocalTestContext.class);

  private static final String CONFIG_ID = "test-config";
  private static final TestStorageConfig STORAGE_CONFIG = TestStorageConfigs.dataDbConfig;
  private static final String MASTER_URL = STORAGE_CONFIG.getPgConfig().getMasterUri();

  public LocalTestContext() {
    super(() -> newInstance(CONFIG_ID, MASTER_URL));
  }

  @Override
  void setupStorage() {
    super.setupStorage();
    if (!MASTER_URL.isBlank()) {
      log.info("Dropping schema {} for url: {}", STORAGE_CONFIG.getMapId(), MASTER_URL);
      SessionOptions sessionOptions = SessionOptions.from(NakshaContext.currentContext(), true);
      PsqlStorage storage = (PsqlStorage) Naksha.useStorage(STORAGE_CONFIG.getPgConfig());
      try (PgSession session = storage.newSession(sessionOptions, false)) {
        session.useConnection().execute("DROP SCHEMA IF EXISTS " + STORAGE_CONFIG.getMapId() + " CASCADE;", null);
        session.commit();
      }
      log.info("Dropped schema: {}", STORAGE_CONFIG.getMapId());
    }
  }
}
