package com.here.naksha.lib.psql;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PsqlPlv8Tests extends PsqlTests {

  @Override
  boolean enabled() {
    // turn me on
    return false;
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
    // naksha_init_v8 should be moved to session
    final SQL sql = pgSession.sql()
        .add("select ")
        .add("evalToText('new plv8.__lib.DummyHello().sayHello()'), ")
        .add("evalToText('new plv8.__lib.DummyHello().add(100,2)')");
    try (PreparedStatement stmt = pgSession.prepareStatement(sql)) {
      try (ResultSet resultSet = stmt.executeQuery()) {
        // then
        resultSet.next();
        assertEquals("hello world", resultSet.getString(2));
        assertEquals("102", resultSet.getString(3));
      }
    }
  }
}
