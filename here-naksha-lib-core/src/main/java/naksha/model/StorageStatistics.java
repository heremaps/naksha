/*
 * Copyright (C) 2017-2024 HERE Europe B.V.
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
package naksha.model;

import java.util.Map;
import naksha.model.StatisticsResponse.Value;
import naksha.model.response.Response;

public class StorageStatistics extends Response {

  private Map<String, SpaceByteSizes> byteSizes;
  private long createdAt;

  public StorageStatistics() {
    super(STORAGE_STATS_TYPE);
  }

  /**
   * @return A map of which the keys are the space IDs and the values are the according byte size
   *     information of the according space.
   */
  public Map<String, SpaceByteSizes> getByteSizes() {
    return byteSizes;
  }

  public void setByteSizes(Map<String, SpaceByteSizes> byteSizes) {
    this.byteSizes = byteSizes;
  }

  public StorageStatistics withByteSizes(Map<String, SpaceByteSizes> byteSizes) {
    setByteSizes(byteSizes);
    return this;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public StorageStatistics withCreatedAt(long createdAt) {
    setCreatedAt(createdAt);
    return this;
  }

  public static class SpaceByteSizes {
    private StatisticsResponse.Value<Long> contentBytes;
    private StatisticsResponse.Value<Long> historyBytes;
    private StatisticsResponse.Value<Long> searchablePropertiesBytes;
    private String error;

    public Value<Long> getContentBytes() {
      return contentBytes;
    }

    public void setContentBytes(Value<Long> contentBytes) {
      this.contentBytes = contentBytes;
    }

    public SpaceByteSizes withContentBytes(Value<Long> contentBytes) {
      setContentBytes(contentBytes);
      return this;
    }

    public Value<Long> getHistoryBytes() {
      return historyBytes;
    }

    public void setHistoryBytes(Value<Long> historyBytes) {
      this.historyBytes = historyBytes;
    }

    public SpaceByteSizes withHistoryBytes(Value<Long> historyBytes) {
      setHistoryBytes(historyBytes);
      return this;
    }

    public Value<Long> getSearchablePropertiesBytes() {
      return searchablePropertiesBytes;
    }

    public void setSearchablePropertiesBytes(Value<Long> searchablePropertiesBytes) {
      this.searchablePropertiesBytes = searchablePropertiesBytes;
    }

    public SpaceByteSizes withSearchablePropertiesBytes(Value<Long> searchablePropertiesBytes) {
      setSearchablePropertiesBytes(searchablePropertiesBytes);
      return this;
    }

    public String getError() {
      return error;
    }

    public void setError(String error) {
      this.error = error;
    }

    public SpaceByteSizes withError(String error) {
      setError(error);
      return this;
    }
  }
}
