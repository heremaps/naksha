package com.here.naksha.lib.extmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.extmanager.utils.AmazonS3Client;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;

public class ExtensionManagerTest {
  @Mock
  INaksha naksha;

  @Test
  public void testGetClassLoaderById() throws IOException {
    ExtConfig extConfig=setupMockConfig();
    JarClient jarClient=mock(JarClient.class);
    when(jarClient.getJar(anyString(),anyString())).thenReturn(mock(File.class));

    ClassLoader loader=mock(ClassLoader.class);
    try(MockedConstruction<ExtensionCache> mockExtensionCache=mockConstruction(ExtensionCache.class,(mock,context)->{
      when(mock.getClassLoaderById("AnyString")).thenReturn(loader);
    })) {
      ExtensionManager extensionManager = spy(new ExtensionManager(naksha, extConfig));
      when(extensionManager.getS3Client()).thenReturn(jarClient);

      Optional<ClassLoader> clsLoader = extensionManager.getClassLoaderById("AnyString");
      assertEquals(loader, clsLoader.get());

      clsLoader = extensionManager.getClassLoaderById("Nothing");
      assertTrue(clsLoader.isEmpty());
    }
  }

  @Test
  public void testGetClassLoaderByName() throws IOException {
    ExtConfig extConfig=setupMockConfig();
    JarClient jarClient=mock(JarClient.class);
    when(jarClient.getJar(anyString(),anyString())).thenReturn(mock(File.class));

    ClassLoader loader=mock(ClassLoader.class);
    try(MockedConstruction<ExtensionCache> mockExtensionCache=mockConstruction(ExtensionCache.class,(mock,context)->{
      when(mock.getClassLoaderByName("AnyString")).thenReturn(loader);
    })) {
      ExtensionManager extensionManager = spy(new ExtensionManager(naksha, extConfig));
      when(extensionManager.getS3Client()).thenReturn(jarClient);

      Optional<ClassLoader> clsLoader = extensionManager.getClassLoaderByName("AnyString");
      assertEquals(loader, clsLoader.get());

      clsLoader = extensionManager.getClassLoaderByName("Nothing");
      assertTrue(clsLoader.isEmpty());

    }
  }

  private ExtConfig setupMockConfig() throws IOException {
    ExtConfig extConfig=mock(ExtConfig.class);
    when(extConfig.getAwsAccessKey()).thenReturn("test");
    when(extConfig.getAwsSecretKey()).thenReturn("test");
    when(extConfig.getAwsRegion()).thenReturn("test");
    when(extConfig.getRefreshScheduleInSeconds()).thenReturn(60l);
    return extConfig;
  }



}
