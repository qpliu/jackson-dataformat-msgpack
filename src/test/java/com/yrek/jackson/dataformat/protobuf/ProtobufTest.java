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

    public static class DataExample {
        @Protobuf(1) public int data;
        @Protobuf(2) public String description;
        @Protobuf(3) public float[] floats;
    }

    public static class MessageExample<T,U> {
        @Protobuf(1) public byte[] binary;
        @Protobuf(2) public T[] ts;
        @Protobuf(3) public U[] us;
    }

    @Test
    public void testSchema() throws Exception {
        Assert.assertEquals("enum EnumExample {\n  A = 1;\n  B = 2;\n  C = 3;\n}\nmessage MessageExampleEnumExampleDataExample {\n  optional bytes binary = 1;\n  repeated EnumExample ts = 2 [packed=true];\n  repeated DataExample us = 3;\n}\nmessage DataExample {\n  optional int32 data = 1;\n  optional string description = 2;\n  repeated float floats = 3 [packed=true];\n}\n", protobufObjectMapper.collectTypes(new TypeReference<MessageExample<EnumExample,DataExample>>() {}).getProtobufDefinition());
    }

    public static class Test1 {
        @Protobuf(value=1, required=true) public int a;
    }

    public static class Test2 {
        @Protobuf(value=2, required=true) public String b;
    }

    public static class Test3 {
        @Protobuf(value=3, required=true) public Test1 c;
    }

    public static class Test4 {
        @Protobuf(4) public int[] d;
    }

    @Test
    public void testExamplesSchema() throws Exception {
        Assert.assertEquals("message Test1 {\n  required int32 a = 1;\n}\n",protobufObjectMapper.collectTypes(Test1.class).getProtobufDefinition());
        Assert.assertEquals("message Test2 {\n  required string b = 2;\n}\n",protobufObjectMapper.collectTypes(Test2.class).getProtobufDefinition());
        Assert.assertEquals("message Test1 {\n  required int32 a = 1;\n}\nmessage Test3 {\n  required Test1 c = 3;\n}\n",protobufObjectMapper.collectTypes(Test3.class).getProtobufDefinition());
        Assert.assertEquals("message Test4 {\n  repeated int32 d = 4 [packed=true];\n}\n",protobufObjectMapper.collectTypes(Test4.class).getProtobufDefinition());
    }

    @Test @org.junit.Ignore("generator not implemented")
    public void testExamplesSerialization() throws Exception {
        Test1 test1 = new Test1();
        test1.a = 150;
        Assert.assertArrayEquals(new byte[] { 0x08, (byte) 0x96, 0x01 }, protobufObjectMapper.writeValueAsBytes(test1));

        Test2 test2 = new Test2();
        test2.b = "testing";
        Assert.assertArrayEquals(new byte[] { 0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6e, 0x67 }, protobufObjectMapper.writeValueAsBytes(test2));

        Test3 test3 = new Test3();
        test3.c = new Test1();
        test3.c.a = 150;
        Assert.assertArrayEquals(new byte[] { 0x1a, 0x03, 0x08, (byte) 0x96, 0x01 }, protobufObjectMapper.writeValueAsBytes(test3));

        Test4 test4 = new Test4();
        test4.d = new int[] { 3, 270, 86942 };
        Assert.assertArrayEquals(new byte[] { 0x22, 0x06, 0x03, (byte) 0x8e, 0x02, (byte) 0x9e, (byte) 0xa7, 0x05 }, protobufObjectMapper.writeValueAsBytes(test4));
    }
}