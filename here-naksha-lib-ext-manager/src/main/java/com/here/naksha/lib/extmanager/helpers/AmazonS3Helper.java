/*
 * Copyright (C) 2017-2023 HERE Europe B.V.
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
package com.here.naksha.lib.extmanager.helpers;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.here.naksha.lib.extmanager.FileClient;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AmazonS3Helper implements FileClient {
  private AmazonS3 s3Client;

  public AmazonS3Helper() {
    s3Client = AmazonS3ClientBuilder.standard()
        .build();
  }

  public File getFile(@NotNull String url) throws IOException {
    String extension = url.substring(url.lastIndexOf("."));
    AmazonS3URI fileUri = new AmazonS3URI(url);
    InputStream inputStream = getS3Object(fileUri);
    File targetFile = File.createTempFile(fileUri.getBucket(), extension);
    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
      byte[] read_buf = new byte[1024];
      int read_len = 0;
      while ((read_len = inputStream.read(read_buf)) > 0) {
        fos.write(read_buf, 0, read_len);
      }
      inputStream.close();
    } catch (IOException e) {
      throw e;
    }
    return targetFile;
  }

  public InputStream getS3Object(AmazonS3URI fileUri) {
    S3Object s3Object = s3Client.getObject(fileUri.getBucket(), fileUri.getKey());
    return s3Object.getObjectContent();
  }

  @Override
  public String getFileContent(String url) throws IOException {
    AmazonS3URI fileUri = new AmazonS3URI(url);
    S3Object s3Object = s3Client.getObject(fileUri.getBucket(), fileUri.getKey());

    // Read the text input stream one line at a time.
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()))) {
      StringBuilder stringBuilder = new StringBuilder("");
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }
      return stringBuilder.toString();
    }
  }

  public List<String> listKeysInBucket(String url) {
    Boolean isTopLevel = false;
    AmazonS3URI fileUri = new AmazonS3URI(url);

    String delimiter = "/";
    if (fileUri.getKey() == "" || fileUri.getKey() == "/") {
      isTopLevel = true;
    }

    ListObjectsV2Request listObjectsRequest = null;
    if (isTopLevel) {
      listObjectsRequest = new ListObjectsV2Request()
          .withBucketName(fileUri.getBucket())
          .withDelimiter(delimiter);
    } else {
      listObjectsRequest = new ListObjectsV2Request()
          .withBucketName(fileUri.getBucket())
          .withPrefix(fileUri.getKey())
          .withDelimiter(delimiter);
    }
    ListObjectsV2Result objects = s3Client.listObjectsV2(listObjectsRequest);
    return objects.getCommonPrefixes();
  }
}
