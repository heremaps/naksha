package com.here.naksha.lib.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

public class TestUtil {

  public static String loadFileOrFail(final @NotNull String rootPath, final @NotNull String fileName) {
    try {
      String json = new String(Files.readAllBytes(Paths.get(rootPath + fileName)));
      return json;
    } catch (IOException e) {
      Assertions.fail("Unable to read test file " + fileName, e);
      return null;
    }
  }
}
