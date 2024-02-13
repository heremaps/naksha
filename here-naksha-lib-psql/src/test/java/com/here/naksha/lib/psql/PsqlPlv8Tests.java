package com.here.naksha.lib.psql;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PsqlPlv8Tests extends PsqlTests {

  @Override
  boolean enabled() {
    return true;
  }

  @Override
  @NotNull String collectionId() {
    return "plv8_test_collection";
  }

  @Override
  boolean partition() {
    return false;
  }

  @Override
  protected PsqlStorage.Params getParams() {
    return super.getParams().pg_plv8(true);
  }

  @Test
  @EnabledIf("runTest")
  @Order(51)
  void testPlv8Function() throws SQLException {
    assert session != null;
    final PostgresSession pgSession = session.session();

    // jbonByteArray = "{"bool":true}"
    byte[] jbonByteArray = new byte[]{18, -116, 0, 0, 17, -123, -60, 98, 111, 111, 108, -47, 2, -32, 2};

    final SQL sql = pgSession.sql()
        .add("SELECT jb_get_bool(?,?,?)");

    try (PreparedStatement stmt = pgSession.prepareStatement(sql)) {
      stmt.setBytes(1, jbonByteArray);
      stmt.setString(2, "bool");
      stmt.setBoolean(3, false); // default value
      try (ResultSet resultSet = stmt.executeQuery()) {
        // then
        resultSet.next();
        assertEquals(true, resultSet.getBoolean(1));
      }
    }
  }
}
