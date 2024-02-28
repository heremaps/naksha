package com.here.naksha.lib.extmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.here.naksha.lib.core.INaksha;
import com.here.naksha.lib.core.models.features.Extension;
import com.here.naksha.lib.core.models.features.ExtensionConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

public class ExtensionManagerTest {
  INaksha naksha=mock(INaksha.class);
  @BeforeEach
  public void setup(){
    when(naksha.getExtensionConfig()).thenReturn(getExtensionConfig());
  }

  @Test
  public void testGetClassLoaderById()  {
    ClassLoader loader=mock(ClassLoader.class);
    try(MockedConstruction<ExtensionCache> mockExtensionCache=mockConstruction(ExtensionCache.class,(mock,context)->{
      when(mock.getClassLoaderById("AnyString")).thenReturn(loader);
    })) {
      ExtensionManager extensionManager = spy(new ExtensionManager(naksha));

      ClassLoader clsLoader = extensionManager.getClassLoader("AnyString");
      assertEquals(loader, clsLoader);

      clsLoader = extensionManager.getClassLoader("Nothing");
      assertNull(clsLoader);
    }
  }

  @Test
  public void testGetCachedExtensions(){
    List<Extension> extList=new ArrayList<>();
    extList.add(new Extension("child_extension_1","url","1.0"));
    try(MockedConstruction<ExtensionCache> mockExtensionCache=mockConstruction(ExtensionCache.class,(mock,context)->{
      when(mock.getCachedExtensions()).thenReturn(extList);
    })) {
      ExtensionManager extensionManager = spy(new ExtensionManager(naksha));

      List<Extension> extensions = extensionManager.getCachedExtensions();
      Assertions.assertEquals(extList.size(),extensions.size());
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
