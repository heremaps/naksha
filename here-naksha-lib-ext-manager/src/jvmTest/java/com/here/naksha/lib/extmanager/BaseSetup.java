package com.here.naksha.lib.extmanager;

import com.here.naksha.lib.core.models.ExtensionConfig;
import com.here.naksha.lib.core.models.ExtensionList;
import com.here.naksha.lib.core.models.features.Extension;
import naksha.base.FromJsonOptions;
import naksha.base.JvmBoxingUtil;
import naksha.base.Base;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BaseSetup {
  public ExtensionConfig getExtensionConfig() {
    return getExtensionConfig("src/jvmTest/resources/data/extension.txt");
  }

  public ExtensionConfig getExtensionConfig(@NotNull String path) {
    List<String> whitelistUrls= Arrays.asList(( "java.*,javax.*,com.here.naksha.*").split(","));
    try {
      String data = Files.readAllLines(Path.of(path)).stream().collect(Collectors.joining());
      List<Extension> list = JvmBoxingUtil.box(Base.fromJSON(data, FromJsonOptions.DEFAULT), ExtensionList.class);
      return new ExtensionConfig(System.currentTimeMillis() + 6000, list,whitelistUrls);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

