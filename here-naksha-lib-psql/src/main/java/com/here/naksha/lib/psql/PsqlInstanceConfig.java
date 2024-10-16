/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package com.here.naksha.lib.psql;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.postgresql.util.HostSpec;

/**
 * Immutable POJO for holding details about a single PostgresQL database instance.
 */
// @JsonDeserialize(using = PsqlInstanceConfigDeserializer.class) // TODO: Fix it, so that it deserializes objects and
// string (urls)!
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings("unused")
public class PsqlInstanceConfig {

  public static final String HOST = "host";

  public static final String PORT = "port";

  public static final String DB = "db";
  public static final String USER = "user";
  public static final String PASSWORD = "password";

  public static final String READ_ONLY = "readOnly";

  /**
   * The database host to connect against.
   */
  @JsonProperty
  public final @NotNull String host;

  /**
   * The database port, defaults to 5432.
   */
  @JsonProperty(defaultValue = "5432")
  @JsonInclude(Include.NON_DEFAULT)
  public final int port;

  /**
   * The database to open.
   */
  @JsonProperty
  public final @NotNull String db;

  /**
   * The user.
   */
  @JsonProperty
  public final @NotNull String user;

  /**
   * The password.
   */
  @JsonProperty
  public final @NotNull String password;

  /**
   * If this is a read-replica or a master node used as read-only.
   */
  @JsonProperty
  @JsonInclude(Include.NON_DEFAULT)
  public final boolean readOnly;

  /**
   * The JDBC URL of the instance configuration.
   */
  @JsonIgnore
  public final @NotNull String url;

  /**
   * The hash-code of the instance configuration.
   */
  @JsonIgnore
  private final int hashCode;

  /**
   * The JSON creator or creator used by
   *
   * @param host     The host to connect to.
   * @param port     The port to connect to, if {@code null}, then defaults to {@code 5432}.
   * @param db       The database to connect to.
   * @param user     The user for authentication.
   * @param password The password for authentication.
   * @param readOnly If the server is read-only (replica).
   */
  @SuppressWarnings("ConstantValue")
  @JsonCreator
  PsqlInstanceConfig(
      @JsonProperty(HOST) @NotNull String host,
      @JsonProperty(PORT) @Nullable Integer port,
      @JsonProperty(DB) @NotNull String db,
      @JsonProperty(USER) @NotNull String user,
      @JsonProperty(PASSWORD) @NotNull String password,
      @JsonProperty(READ_ONLY) @Nullable Boolean readOnly) {
    if (host == null || host.length() == 0) {
      throw new IllegalArgumentException("host is empty");
    }
    if (db == null || db.length() == 0) {
      throw new IllegalArgumentException("db is empty");
    }
    if (port == null) {
      port = 5432;
    }
    if (port <= 0 || port >= 65536) {
      throw new IllegalArgumentException("port is invalid (not between 1 and 65535)");
    }
    if (user == null || user.length() == 0) {
      throw new IllegalArgumentException("user is empty");
    }
    if (password == null || password.length() == 0) {
      throw new IllegalArgumentException("password is empty");
    }
    this.host = host;
    this.port = port;
    this.db = db;
    this.user = user;
    this.password = password;
    this.readOnly = readOnly != null ? readOnly : false;
    this.url = "jdbc:postgresql://" + host + (this.port == 5432 ? "" : ":" + this.port) + "/" + db
        + (isTrue(readOnly) ? "?readOnly=true" : "");
    this.hashCode = Objects.hash(url, this.user, this.password);
    this.hostSpec = new HostSpec(this.host, this.port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass() != PsqlInstanceConfig.class) {
      return false;
    }
    final PsqlInstanceConfig that = (PsqlInstanceConfig) o;
    return hashCode == that.hashCode
        && url.equals(that.url)
        && user.equals(that.user)
        && password.equals(that.password);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return url;
  }

  /**
   * The host specification for the PostgresQL driver.
   */
  @JsonIgnore
  public final @NotNull HostSpec hostSpec;
}
