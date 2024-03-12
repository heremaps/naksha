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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;

public class BaseSetup {
  @BeforeAll
  public static void setup(){
    System.setProperty("aws.accessKeyId", "test");
    System.setProperty("aws.secretKey", "test");
    System.setProperty("aws.region", "eu-west-1");
  }
  public ExtensionConfig getExtensionConfig() {
    List<String> whitelistUrls= Arrays.asList(( "java.*,javax.*,com.here.naksha.*").split(","));
    Path file = new File("src/test/resources/data/extension.txt").toPath();
    List<Extension> list;
    try {
      String data = Files.readAllLines(file).stream().collect(Collectors.joining());
      list = new ObjectMapper().readValue(data, new TypeReference<List<Extension>>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new ExtensionConfig(System.currentTimeMillis() + 6000, list,"",whitelistUrls);
  }
}
