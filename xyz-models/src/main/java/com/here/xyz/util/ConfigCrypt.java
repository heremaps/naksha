/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.xyz.util;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.kms.model.EncryptRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ConfigCrypt {

  private static AWSKMS kmsClient;
  public static final String encPrefix = "{{";
  public static final String encPostfix = "}}";

  public static @Nullable String decryptString(@Nullable String value, @Nonnull String keyId) throws CryptoException {
    if (value != null
        && value.startsWith(encPrefix)
        && value.endsWith(encPostfix)) {
      final byte[] bytes = Base64.getDecoder().decode(value.substring(2, value.length() - 2));
      return new String(decryptBytes(bytes, keyId), StandardCharsets.UTF_8);
    }
    return value;
  }

  public static @Nullable String encryptString(@Nullable String value, @Nonnull String keyId) throws CryptoException {
    if (value == null || (value.startsWith(encPrefix) && value.endsWith(encPostfix))) {
      return value;
    }
    final byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
    final byte[] encrypted = encryptBytes(valueBytes, keyId);
    return encPrefix + Base64.getEncoder().withoutPadding().encodeToString(encrypted) + encPostfix;
  }

  public static boolean isEncryptedString(@Nullable String str) {
    return str != null
        && str.length() > (encPrefix.length() + encPostfix.length())
        && str.startsWith(encPrefix)
        && str.endsWith(encPostfix);
  }

  public static @Nonnull byte[] decryptBytes(@Nonnull byte[] bytes, @Nonnull String keyId) throws CryptoException {
    final ByteBuffer cipherTextBlob = ByteBuffer.wrap(bytes);
    final DecryptRequest req = new DecryptRequest().withKeyId(keyId).withCiphertextBlob(cipherTextBlob);
    try {
      return getKmsClient().decrypt(req).getPlaintext().array();
    } catch (RuntimeException e) {
      throw new CryptoException("Error when trying to decrypt value.");
    }
  }

  public static @Nonnull byte[] encryptBytes(@Nonnull byte[] value, @Nonnull String keyId) throws CryptoException {
    final ByteBuffer plainText = ByteBuffer.wrap(value);
    final EncryptRequest req = new EncryptRequest().withKeyId(keyId).withPlaintext(plainText);
    try {
      final ByteBuffer encrypted = getKmsClient().encrypt(req).getCiphertextBlob();
      return encrypted.array();
    } catch (RuntimeException e) {
      throw new CryptoException("Error when trying to encrypt value.");
    }
  }

  public static class CryptoException extends Exception {

    public CryptoException(String message) {
      super(message);
    }

    public CryptoException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  private static AWSKMS getKmsClient() {
    if (kmsClient == null) {
      if (Regions.getCurrentRegion() == null) {
        kmsClient = AWSKMSClientBuilder.standard().withRegion("us-east-1").build();
      } else {
        kmsClient = AWSKMSClientBuilder.defaultClient();
      }
    }
    return kmsClient;
  }
}
