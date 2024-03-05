package com.here.naksha.lib.extmanager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.models.ExtensionConfig;
import com.here.naksha.lib.core.models.features.Extension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BaseSetup {
  public ExtensionConfig getExtensionConfig() {
    List<String> whitelistUrls= Arrays.asList(( "java.*,javax.*,com.here.naksha.*").split(","));
    ExtensionConfig extensionConfig=new ExtensionConfig(6000,"");
    extensionConfig.setWhilelistDelegateClass(whitelistUrls);

    Path file = new File("src/test/resources/data/extension.txt").toPath();
    List<Extension> list;
    try {
      String data = Files.readAllLines(file).stream().collect(Collectors.joining());
      list = new ObjectMapper().readValue(data, new TypeReference<List<Extension>>() {});
      extensionConfig.setExtensions(list);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return extensionConfig;
  }
}
