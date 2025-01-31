/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.io.parquet.convert;

import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector.Category;
import org.apache.hadoop.hive.serde2.typeinfo.DecimalTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.parquet.schema.ConversionPatterns;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Type.Repetition;
import org.apache.parquet.schema.Types;

public class HiveSchemaConverter {

  public static MessageType convert(final List<String> columnNames,
      final List<TypeInfo> columnTypes) {
    return convert(columnNames, columnTypes, null);
  }

  public static MessageType convert(final List<String> columnNames,
      final List<TypeInfo> columnTypes, Configuration conf) {
    final MessageType schema =
        new MessageType("hive_schema", convertTypes(columnNames, columnTypes, conf));
    return schema;
  }

  private static Type[] convertTypes(final List<String> columnNames,
      final List<TypeInfo> columnTypes, Configuration conf) {
    if (columnNames.size() != columnTypes.size()) {
      throw new IllegalStateException("Mismatched Hive columns and types. Hive columns names" +
        " found : " + columnNames + " . And Hive types found : " + columnTypes);
    }
    final Type[] types = new Type[columnNames.size()];
    for (int i = 0; i < columnNames.size(); ++i) {
      types[i] = convertType(columnNames.get(i), columnTypes.get(i), conf);
    }
    return types;
  }

  private static Type convertType(final String name, final TypeInfo typeInfo, Configuration conf) {
    return convertType(name, typeInfo, conf, Repetition.OPTIONAL);
  }

  private static Type convertType(final String name, final TypeInfo typeInfo, Configuration conf,
                                  final Repetition repetition) {
    if (typeInfo.getCategory().equals(Category.PRIMITIVE)) {
      if (typeInfo.equals(TypeInfoFactory.stringTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.BINARY, repetition)
            .as(LogicalTypeAnnotation.stringType()).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.intTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.INT32, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.shortTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.INT32, repetition)
            .as(LogicalTypeAnnotation.intType(16, true)).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.byteTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.INT32, repetition)
            .as(LogicalTypeAnnotation.intType(8, true)).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.longTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.INT64, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.doubleTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.DOUBLE, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.floatTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.FLOAT, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.booleanTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.BOOLEAN, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.binaryTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.BINARY, repetition).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.timestampTypeInfo)) {
        boolean useInt64;
        String timeUnitVal;
        if (conf != null) {
          useInt64 = HiveConf.getBoolVar(conf, HiveConf.ConfVars.HIVE_PARQUET_WRITE_INT64_TIMESTAMP);
          timeUnitVal = HiveConf.getVar(conf, HiveConf.ConfVars.HIVE_PARQUET_TIMESTAMP_TIME_UNIT);
        } else { //use defaults
          useInt64 = HiveConf.ConfVars.HIVE_PARQUET_WRITE_INT64_TIMESTAMP.defaultBoolVal;
          timeUnitVal = HiveConf.ConfVars.HIVE_PARQUET_TIMESTAMP_TIME_UNIT.defaultStrVal;
        }
        if (useInt64) {
          LogicalTypeAnnotation.TimeUnit timeUnit =
              LogicalTypeAnnotation.TimeUnit.valueOf(timeUnitVal.toUpperCase());
          return Types.primitive(PrimitiveTypeName.INT64, repetition)
              .as(LogicalTypeAnnotation.timestampType(false, timeUnit)).named(name);
        } else {
          return Types.primitive(PrimitiveTypeName.INT96, repetition).named(name);
        }
      } else if (typeInfo.equals(TypeInfoFactory.voidTypeInfo)) {
        throw new UnsupportedOperationException("Void type not implemented");
      } else if (typeInfo.getTypeName().startsWith(
          serdeConstants.CHAR_TYPE_NAME)) {
        return Types.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType())
            .named(name);
      } else if (typeInfo.getTypeName().startsWith(
          serdeConstants.VARCHAR_TYPE_NAME)) {
        return Types.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType())
            .named(name);
      } else if (typeInfo instanceof DecimalTypeInfo) {
        DecimalTypeInfo decimalTypeInfo = (DecimalTypeInfo) typeInfo;
        int prec = decimalTypeInfo.precision();
        int scale = decimalTypeInfo.scale();
        int bytes = ParquetHiveSerDe.PRECISION_TO_BYTE_COUNT[prec - 1];
        return Types.optional(PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY).length(bytes)
            .as(LogicalTypeAnnotation.decimalType(scale, prec)).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.dateTypeInfo)) {
        return Types.primitive(PrimitiveTypeName.INT32, repetition)
            .as(LogicalTypeAnnotation.dateType()).named(name);
      } else if (typeInfo.equals(TypeInfoFactory.unknownTypeInfo)) {
        throw new UnsupportedOperationException("Unknown type not implemented");
      } else {
        throw new IllegalArgumentException("Unknown type: " + typeInfo);
      }
    } else if (typeInfo.getCategory().equals(Category.LIST)) {
      return convertArrayType(name, (ListTypeInfo) typeInfo, conf);
    } else if (typeInfo.getCategory().equals(Category.STRUCT)) {
      return convertStructType(name, (StructTypeInfo) typeInfo, conf);
    } else if (typeInfo.getCategory().equals(Category.MAP)) {
      return convertMapType(name, (MapTypeInfo) typeInfo, conf);
    } else if (typeInfo.getCategory().equals(Category.UNION)) {
      throw new UnsupportedOperationException("Union type not implemented");
    } else {
      throw new IllegalArgumentException("Unknown type: " + typeInfo);
    }
  }

  // An optional group containing a repeated anonymous group "bag", containing
  // 1 anonymous element "array_element"
  private static GroupType convertArrayType(final String name, final ListTypeInfo typeInfo, final Configuration conf) {
    final TypeInfo subType = typeInfo.getListElementTypeInfo();
    GroupType groupType = Types.optionalGroup().as(LogicalTypeAnnotation.listType())
        .addField(Types.repeatedGroup().addField(convertType("array_element", subType, conf))
            .named(ParquetHiveSerDe.ARRAY.toString()))
        .named(name);
    return groupType;
  }

  // An optional group containing multiple elements
  private static GroupType convertStructType(final String name, final StructTypeInfo typeInfo,
      final Configuration conf) {
    final List<String> columnNames = typeInfo.getAllStructFieldNames();
    final List<TypeInfo> columnTypes = typeInfo.getAllStructFieldTypeInfos();
    GroupType groupType = Types.optionalGroup().addFields(convertTypes(columnNames, columnTypes, conf)).named(name);
    return groupType;
  }

  // An optional group containing a repeated anonymous group "map", containing
  // 2 elements: "key", "value"
  private static GroupType convertMapType(final String name, final MapTypeInfo typeInfo,
      final Configuration conf) {
    final Type keyType = convertType(ParquetHiveSerDe.MAP_KEY.toString(),
        typeInfo.getMapKeyTypeInfo(), conf, Repetition.REQUIRED);
    final Type valueType = convertType(ParquetHiveSerDe.MAP_VALUE.toString(),
        typeInfo.getMapValueTypeInfo(), conf);
    return ConversionPatterns.mapType(Repetition.OPTIONAL, name, keyType, valueType);
  }
}
