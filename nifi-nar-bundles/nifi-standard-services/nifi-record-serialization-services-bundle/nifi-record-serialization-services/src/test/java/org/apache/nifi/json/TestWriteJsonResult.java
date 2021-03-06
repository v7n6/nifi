/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nifi.json;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.schema.access.SchemaNameAsAttribute;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.DataType;
import org.apache.nifi.serialization.record.MapRecord;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.RecordSchema;
import org.apache.nifi.serialization.record.RecordSet;
import org.apache.nifi.serialization.record.SerializedForm;
import org.junit.Test;
import org.mockito.Mockito;

public class TestWriteJsonResult {

    @Test
    public void testDataTypes() throws IOException, ParseException {
        final List<RecordField> fields = new ArrayList<>();
        for (final RecordFieldType fieldType : RecordFieldType.values()) {
            if (fieldType == RecordFieldType.CHOICE) {
                final List<DataType> possibleTypes = new ArrayList<>();
                possibleTypes.add(RecordFieldType.INT.getDataType());
                possibleTypes.add(RecordFieldType.LONG.getDataType());

                fields.add(new RecordField(fieldType.name().toLowerCase(), fieldType.getChoiceDataType(possibleTypes)));
            } else if (fieldType == RecordFieldType.MAP) {
                fields.add(new RecordField(fieldType.name().toLowerCase(), fieldType.getMapDataType(RecordFieldType.INT.getDataType())));
            } else {
                fields.add(new RecordField(fieldType.name().toLowerCase(), fieldType.getDataType()));
            }
        }
        final RecordSchema schema = new SimpleRecordSchema(fields);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        df.setTimeZone(TimeZone.getTimeZone("gmt"));
        final long time = df.parse("2017/01/01 17:00:00.000").getTime();

        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("height", 48);
        map.put("width", 96);

        final Map<String, Object> valueMap = new LinkedHashMap<>();
        valueMap.put("string", "string");
        valueMap.put("boolean", true);
        valueMap.put("byte", (byte) 1);
        valueMap.put("char", 'c');
        valueMap.put("short", (short) 8);
        valueMap.put("int", 9);
        valueMap.put("bigint", BigInteger.valueOf(8L));
        valueMap.put("long", 8L);
        valueMap.put("float", 8.0F);
        valueMap.put("double", 8.0D);
        valueMap.put("date", new Date(time));
        valueMap.put("time", new Time(time));
        valueMap.put("timestamp", new Timestamp(time));
        valueMap.put("record", null);
        valueMap.put("array", null);
        valueMap.put("choice", 48L);
        valueMap.put("map", map);

        final Record record = new MapRecord(schema, valueMap);
        final RecordSet rs = RecordSet.of(schema, record);

        try (final WriteJsonResult writer = new WriteJsonResult(Mockito.mock(ComponentLog.class), schema, new SchemaNameAsAttribute(), baos, true, RecordFieldType.DATE.getDefaultFormat(),
            RecordFieldType.TIME.getDefaultFormat(), RecordFieldType.TIMESTAMP.getDefaultFormat())) {

            writer.write(rs);
        }

        final String output = baos.toString();

        final String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/json/output/dataTypes.json")));
        assertEquals(expected, output);
    }


    @Test
    public void testWriteSerializedForm() throws IOException {
        final List<RecordField> fields = new ArrayList<>();
        fields.add(new RecordField("name", RecordFieldType.STRING.getDataType()));
        fields.add(new RecordField("age", RecordFieldType.INT.getDataType()));
        final RecordSchema schema = new SimpleRecordSchema(fields);

        final Map<String, Object> values1 = new HashMap<>();
        values1.put("name", "John Doe");
        values1.put("age", 42);
        final String serialized1 = "{ \"name\": \"John Doe\",    \"age\": 42 }";
        final SerializedForm serializedForm1 = SerializedForm.of(serialized1, "application/json");
        final Record record1 = new MapRecord(schema, values1, serializedForm1);

        final Map<String, Object> values2 = new HashMap<>();
        values2.put("name", "Jane Doe");
        values2.put("age", 43);
        final String serialized2 = "{ \"name\": \"Jane Doe\",    \"age\": 43 }";
        final SerializedForm serializedForm2 = SerializedForm.of(serialized2, "application/json");
        final Record record2 = new MapRecord(schema, values1, serializedForm2);

        final RecordSet rs = RecordSet.of(schema, record1, record2);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final WriteJsonResult writer = new WriteJsonResult(Mockito.mock(ComponentLog.class), schema, new SchemaNameAsAttribute(), baos, true, RecordFieldType.DATE.getDefaultFormat(),
            RecordFieldType.TIME.getDefaultFormat(), RecordFieldType.TIMESTAMP.getDefaultFormat())) {

            writer.write(rs);
        }

        final byte[] data = baos.toByteArray();

        final String expected = "[ " + serialized1 + ", " + serialized2 + " ]";

        final String output = new String(data, StandardCharsets.UTF_8);
        assertEquals(expected, output);
    }

    @Test
    public void testTimestampWithNullFormat() throws IOException {
        final Map<String, Object> values = new HashMap<>();
        values.put("timestamp", new java.sql.Timestamp(37293723L));
        values.put("time", new java.sql.Time(37293723L));
        values.put("date", new java.sql.Date(37293723L));

        final List<RecordField> fields = new ArrayList<>();
        fields.add(new RecordField("timestamp", RecordFieldType.TIMESTAMP.getDataType()));
        fields.add(new RecordField("time", RecordFieldType.TIME.getDataType()));
        fields.add(new RecordField("date", RecordFieldType.DATE.getDataType()));

        final RecordSchema schema = new SimpleRecordSchema(fields);

        final Record record = new MapRecord(schema, values);
        final RecordSet rs = RecordSet.of(schema, record);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final WriteJsonResult writer = new WriteJsonResult(Mockito.mock(ComponentLog.class), schema, new SchemaNameAsAttribute(), baos, false, null, null, null)) {
            writer.write(rs);
        }

        final byte[] data = baos.toByteArray();

        final String expected = "[{\"timestamp\":37293723,\"time\":37293723,\"date\":37293723}]";

        final String output = new String(data, StandardCharsets.UTF_8);
        assertEquals(expected, output);
    }
}
