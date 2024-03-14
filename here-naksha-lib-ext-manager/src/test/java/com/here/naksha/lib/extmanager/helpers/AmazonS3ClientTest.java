package com.here.naksha.lib.extmanager.helpers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.here.naksha.lib.extmanager.BaseSetup;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AmazonS3ClientTest extends BaseSetup {

  @Test
  public void testGetFile() throws IOException {
    AmazonS3Helper s3Helper= Mockito.spy(new AmazonS3Helper());
    doReturn(new FileInputStream("src/test/resources/data/extension.txt")).when(s3Helper).getS3Object(any());
    File file=s3Helper.getFile("s3://naksa-test/test.jar");
    Assertions.assertNotNull(file);
  }

  @Test
  public void testGetFileContent() throws IOException {
    final String fileName="src/test/resources/data/extension.txt";
    String data= Files.readAllLines(Paths.get(fileName)).stream().collect(Collectors.joining());
    AmazonS3Helper s3Helper= Mockito.spy(new AmazonS3Helper());
    doReturn(new FileInputStream(fileName)).when(s3Helper).getS3Object(any());
    Assertions.assertEquals(data, s3Helper.getFileContent("s3://naksa-test/test.jar"));
  }

}
