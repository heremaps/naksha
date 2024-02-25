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
package com.here.naksha.lib.extmanager.utils;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.here.naksha.lib.extmanager.JarClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmazonS3Client implements JarClient {
  private static final @NotNull Logger logger = LoggerFactory.getLogger(AmazonS3Client.class);
  private AmazonS3 s3Client;
  private final String tempPath;

  public AmazonS3Client(String aws_access_key, String aws_secret_key, String aws_region, String tempPath) {
    this.tempPath = tempPath;
    BasicAWSCredentials awsCredentials = new BasicAWSCredentials(aws_access_key, aws_secret_key);
    s3Client = AmazonS3ClientBuilder.standard()
        .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
        .withRegion(aws_region)
        .build();
  }

  public File getJar(String bucketName, String s3Key) throws IOException {
    logger.info("Start loading file : {} , in bucket : {}", s3Key, bucketName);
    String fileName = getFileNameFromKey(s3Key);
    S3Object s3Object = s3Client.getObject(bucketName, s3Key);
    S3ObjectInputStream inputStream = s3Object.getObjectContent();
    File targetFile = new File(fileName);
    try {
      FileOutputStream fos = new FileOutputStream(new File(fileName));
      byte[] read_buf = new byte[1024];
      int read_len = 0;
      while ((read_len = inputStream.read(read_buf)) > 0) {
        fos.write(read_buf, 0, read_len);
      }
      inputStream.close();
      fos.close();
    } catch (IOException e) {
      throw e;
    }
    //    java.nio.file.Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return targetFile;
  }

  protected String getFileNameFromKey(String keyPath) {
    String[] bits = keyPath.split("/");
    String fileName = bits[bits.length - 1];
    return fileName;
  }
}
