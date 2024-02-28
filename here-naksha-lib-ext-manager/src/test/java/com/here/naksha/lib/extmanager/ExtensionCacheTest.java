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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.features.ExtensionConfig;
import com.here.naksha.lib.extmanager.helpers.AmazonS3Helper;
import com.here.naksha.lib.extmanager.helpers.ClassLoaderHelper;
import com.here.naksha.lib.extmanager.helpers.FileHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

public class ExtensionCacheTest {

  @Mock
  INaksha naksha;
  @Test
  public void testBuildExtensionCache() throws IOException {
    ClassLoader classLoader=mock(ClassLoader.class);
    ExtensionConfig extensionConfig=getExtensionConfig();
    try(MockedStatic<ClassLoaderHelper> mockedStatic=mockStatic(ClassLoaderHelper.class)) {
      when(ClassLoaderHelper.getClassLoader(any(),anyList())).thenReturn(classLoader);
      ExtensionCache extensionCache =spy( new ExtensionCache(naksha));
      extensionCache.buildExtensionCache(extensionConfig);
      Assertions.assertEquals(2,extensionCache.getCacheLength());

      extensionConfig.getExtensions().remove(0);
      extensionCache.buildExtensionCache(extensionConfig);
      Assertions.assertEquals(1,extensionCache.getCacheLength());
    }
  }

  @Test
  public void testGetJarClient(){
    ExtensionCache extensionCache=new ExtensionCache(naksha);
    JarClient jarClient=extensionCache.getJarClient("s3://bucket/test.jar");
    Assertions.assertTrue(jarClient instanceof AmazonS3Helper);

    JarClient jarClient1=extensionCache.getJarClient("s3://bucket/test1.jar");
    Assertions.assertEquals(jarClient,jarClient1);

    jarClient=extensionCache.getJarClient("file://bucket/test.jar");
    Assertions.assertTrue(jarClient instanceof FileHelper);

    Assertions.assertThrows(UnsupportedOperationException.class,()->extensionCache.getJarClient("error://bucket/test.jar"));
  }

  @Test
  public void testGetClassLoaderById(){
    ClassLoader classLoader=mock(ClassLoader.class);
    ExtensionConfig extensionConfig=getExtensionConfig();
    try(MockedStatic<ClassLoaderHelper> mockedStatic=mockStatic(ClassLoaderHelper.class)) {
      when(ClassLoaderHelper.getClassLoader(any(),anyList())).thenReturn(classLoader);
      ExtensionCache extensionCache =spy(new ExtensionCache(naksha));
      extensionCache.buildExtensionCache(extensionConfig);
      Assertions.assertEquals(2,extensionCache.getCacheLength());

      ClassLoader loader=extensionCache.getClassLoaderById(extensionConfig.getExtensions().get(0).getExtensionId());
      Assertions.assertNotNull(loader);
      Assertions.assertEquals(classLoader,loader);
    }
  }

  @Test
  public void testGetCachedExtensions(){
    ClassLoader classLoader=mock(ClassLoader.class);
    ExtensionConfig extensionConfig=getExtensionConfig();
    try(MockedStatic<ClassLoaderHelper> mockedStatic=mockStatic(ClassLoaderHelper.class)) {
      when(ClassLoaderHelper.getClassLoader(any(),anyList())).thenReturn(classLoader);
      ExtensionCache extensionCache =spy( new ExtensionCache(naksha));
      extensionCache.buildExtensionCache(extensionConfig);
      Assertions.assertEquals(2,extensionCache.getCachedExtensions().size());


      extensionConfig.getExtensions().remove(0);
      extensionCache.buildExtensionCache(extensionConfig);
      Assertions.assertEquals(1,extensionCache.getCachedExtensions().size());
      Assertions.assertEquals(extensionConfig.getExtensions().get(0).getExtensionId(),extensionCache.getCachedExtensions().get(0).getExtensionId());
    }
  }


  private ExtensionConfig getExtensionConfig() {
    Path file = new File("src/test/resources/data/extension.txt").toPath();
    ExtensionConfig extensionConfig=null;
    try {
      String data = Files.readAllLines(file).stream().collect(Collectors.joining());
      extensionConfig = new ObjectMapper().readValue(data, ExtensionConfig.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return extensionConfig;
  }

}
