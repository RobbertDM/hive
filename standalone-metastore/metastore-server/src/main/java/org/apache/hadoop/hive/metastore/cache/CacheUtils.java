/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.metastore.cache;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.hive.metastore.RawStore;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.SkewedInfo;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.cache.SharedCache.PartitionWrapper;
import org.apache.hadoop.hive.metastore.cache.SharedCache.TableWrapper;
import org.apache.hadoop.hive.metastore.utils.StringUtils;

import static org.apache.hadoop.hive.metastore.cache.CachedStore.shouldCacheTable;

public class CacheUtils {
  private static final String delimit = "\u0001";

  /**
   * Constant variable that stores engine value needed to store / access
   * Hive column statistics.
   * TODO: Once CachedStore supports multiple engines, this constant variable
   * can be removed.
   */
  protected static final String HIVE_ENGINE = "hive";

  public static String buildCatalogKey(String catName) {
    return catName;
  }

  public static String buildDbKey(String catName, String dbName) {
    return buildKey(catName, dbName);
  }

  public static String buildDbKeyWithDelimiterSuffix(String catName, String dbName) {
    return buildKey(catName, dbName) + delimit;
  }

  /**
   * Builds a key for the partition cache which is concatenation of partition values, each value
   * separated by a delimiter
   *
   */
  public static String buildPartitionCacheKey(List<String> partVals) {
    return CollectionUtils.isNotEmpty(partVals) ? String.join(delimit, partVals) : "";
  }

  public static String buildTableKey(String catName, String dbName, String tableName) {
    return buildKey(StringUtils.normalizeIdentifier(catName), StringUtils.normalizeIdentifier(dbName),
        StringUtils.normalizeIdentifier(tableName));
  }

  public static String buildTableColKey(String catName, String dbName, String tableName,
                                        String colName) {
    return buildKey(catName, dbName, tableName, colName);
  }

  public static String buildKey(String... elements) {
    return org.apache.commons.lang3.StringUtils.join(elements, delimit);
  }

  public static String[] splitDbName(String key) {
    String[] names = key.split(delimit);
    assert names.length == 2;
    return names;
  }

  /**
   * Builds a key for the partitions column cache which is concatenation of partition values, each
   * value separated by a delimiter and the column name
   *
   */
  public static String buildPartitonColStatsCacheKey(List<String> partVals, String colName) {
    return buildPartitionCacheKey(partVals) + delimit + colName;
  }

  static Table assemble(TableWrapper wrapper, SharedCache sharedCache) {
    Table t = wrapper.getTable().deepCopy();
    if (wrapper.getSdHash() != null) {
      StorageDescriptor sdCopy = sharedCache.getSdFromCache(wrapper.getSdHash()).deepCopy();
      if (sdCopy.getBucketCols() == null) {
        sdCopy.setBucketCols(Collections.emptyList());
      }
      if (sdCopy.getSortCols() == null) {
        sdCopy.setSortCols(Collections.emptyList());
      }
      if (sdCopy.getSkewedInfo() == null) {
        sdCopy.setSkewedInfo(new SkewedInfo(Collections.emptyList(),
          Collections.emptyList(), Collections.emptyMap()));
      }
      sdCopy.setLocation(wrapper.getLocation());
      sdCopy.setParameters(wrapper.getParameters());
      t.setSd(sdCopy);
    }
    return t;
  }

  static Partition assemble(PartitionWrapper wrapper, SharedCache sharedCache) {
    Partition p = wrapper.getPartition().deepCopy();
    if (wrapper.getSdHash() != null) {
      StorageDescriptor sdCopy = sharedCache.getSdFromCache(wrapper.getSdHash()).deepCopy();
      if (sdCopy.getBucketCols() == null) {
        sdCopy.setBucketCols(Collections.emptyList());
      }
      if (sdCopy.getSortCols() == null) {
        sdCopy.setSortCols(Collections.emptyList());
      }
      if (sdCopy.getSkewedInfo() == null) {
        sdCopy.setSkewedInfo(new SkewedInfo(Collections.emptyList(),
          Collections.emptyList(), Collections.emptyMap()));
      }
      sdCopy.setLocation(wrapper.getLocation());
      sdCopy.setParameters(wrapper.getParameters());
      p.setSd(sdCopy);
    }
    return p;
  }

  public static boolean matches(String name, String pattern) {
    String[] subpatterns = pattern.trim().split("\\|");
    for (String subpattern : subpatterns) {
      subpattern = "(?i)" + subpattern.replaceAll("\\?", ".{1}").replaceAll("\\*", ".*")
          .replaceAll("\\^", "\\\\^").replaceAll("\\$", "\\\\$");
      if (Pattern.matches(subpattern, StringUtils.normalizeIdentifier(name))) {
        return true;
      }
    }
    return false;
  }
}
