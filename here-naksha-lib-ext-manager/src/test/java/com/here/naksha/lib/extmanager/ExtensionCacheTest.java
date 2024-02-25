package com.here.naksha.lib.extmanager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.extmanager.models.ExtensionMetaData;
import com.here.naksha.lib.extmanager.utils.ClassLoaderHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class ExtensionCacheTest {

  @Test
  public void testBuildExtensionCache() throws IOException {
    ClassLoader classLoader=mock(ClassLoader.class);
    List<ExtensionMetaData> metaDataList=getExtensions();
    ExtConfig extConfig=setupMockConfig();
    JarClient jarClient=mock(JarClient.class);
    when(jarClient.getJar(anyString(),anyString())).thenReturn(mock(File.class));

    try(MockedStatic<ClassLoaderHelper> mockedStatic=mockStatic(ClassLoaderHelper.class)) {
      when(ClassLoaderHelper.getClassLoader(any(),anyList())).thenReturn(classLoader);
      ExtensionCache extensionCache =spy( new ExtensionCache(jarClient));
      extensionCache.buildExtensionCache(metaDataList, extConfig);
      Assertions.assertEquals(2,extensionCache.getCacheLength());
      verify(jarClient,times(2)).getJar(anyString(),anyString());
    }

  }
  private ExtConfig setupMockConfig() throws IOException {
    ExtConfig extConfig=mock(ExtConfig.class);
    when(extConfig.getAwsAccessKey()).thenReturn("test");
    when(extConfig.getAwsSecretKey()).thenReturn("test");
    when(extConfig.getAwsRegion()).thenReturn("test");
    when(extConfig.getAwsBucket()).thenReturn("test");
    when(extConfig.getRefreshScheduleInSeconds()).thenReturn(60l);
    return extConfig;
  }

  private List<ExtensionMetaData> getExtensions() {
    Path file = new File("src/test/resources/data/extension.txt").toPath();
    List<ExtensionMetaData> list = new ArrayList<>();
    try {
      String data = Files.readAllLines(file).stream().collect(Collectors.joining());
      list = new ObjectMapper().readValue(data, new TypeReference<List<ExtensionMetaData>>() {});
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return list;
  }
}
