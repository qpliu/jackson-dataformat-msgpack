package com.yrek.jackson.dataformat.protobuf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.junit.Assert;
import org.junit.Test;

public class ProtobufTest {
    private ObjectMapper jsonMapper = new ObjectMapper();
    private ProtobufObjectMapper protobufObjectMapper = new ProtobufObjectMapper();

    public enum EnumExample {
        @Protobuf(1) A,
        @Protobuf(2) B,
        @Protobuf(3) C,
    }

    public class DataExample {
        @Protobuf(1) public int data;
        @Protobuf(2) public String description;
        @Protobuf(3) public float[] floats;
    }

    public class MessageExample<T,U> {
        @Protobuf(1) public byte[] binary;
        @Protobuf(2) public T[] ts;
        @Protobuf(3) public U[] us;
    }

    @Test
    public void testSchema() throws Exception {
        Assert.assertEquals("enum ProtobufTest_EnumExample {\n  A = 1;\n  B = 2;\n  C = 3;\n}\nmessage ProtobufTest_MessageExampleProtobufTest_EnumExampleProtobufTest_DataExample {\n  optional bytes binary = 1;\n  repeated ProtobufTest_EnumExample ts = 2 [packed=true];\n  repeated ProtobufTest_DataExample us = 3;\n}\nmessage ProtobufTest_DataExample {\n  optional int32 data = 1;\n  optional string description = 2;\n  repeated float floats = 3 [packed=true];\n}\n", protobufObjectMapper.collectTypes(new TypeReference<MessageExample<EnumExample,DataExample>>() {}).getProtobufDefinition());
    }
}
