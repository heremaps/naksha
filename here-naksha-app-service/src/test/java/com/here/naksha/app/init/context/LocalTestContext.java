package com.here.naksha.app.init.context;

import static com.here.naksha.app.service.NakshaApp.newInstance;

import com.here.naksha.app.init.TestPsqlConfig;
import com.here.naksha.app.init.TestPsqlStorageConfigs;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.psql.PgCursor;
import naksha.psql.PsqlCluster;
import naksha.psql.PsqlInstance;
import naksha.psql.PsqlSession;
import naksha.psql.PsqlStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalTestContext extends TestContext {

  private static final Logger log = LoggerFactory.getLogger(LocalTestContext.class);

  private static final String CONFIG_ID = "test-config";
  private static final TestPsqlConfig STORAGE_CONFIG = TestPsqlStorageConfigs.dataDbConfig;

  public LocalTestContext() {
    super(() -> newInstance(CONFIG_ID, STORAGE_CONFIG.url()));
  }


  @Override
    // TODO CASL-834: switch to proper schema dropping
  void setupStorage() {
    super.setupStorage();
    if (!STORAGE_CONFIG.url().isBlank()) {
      log.info("Dropping schema {} for url: {}", STORAGE_CONFIG.schema(), STORAGE_CONFIG.url());
      SessionOptions sessionOptions = SessionOptions.from(NakshaContext.currentContext(), true);
      try (PsqlSession session = storage().newSession(sessionOptions, false)) {
        session.usePgConnection().execute("DROP SCHEMA IF EXISTS " + STORAGE_CONFIG.schema() + " CASCADE;", null);
        session.commit();
      }
      log.info("Dropped schema: {}", STORAGE_CONFIG.schema());
    }
  }

  private PsqlStorage storage() {
    PsqlInstance instance = PsqlInstance.get(STORAGE_CONFIG.url());
    PsqlCluster cluster = new PsqlCluster(instance);
    return new PsqlStorage(cluster, STORAGE_CONFIG.schema());
  }
}
