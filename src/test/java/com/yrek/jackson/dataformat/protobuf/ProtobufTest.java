package com.yrek.jackson.dataformat.protobuf;

import java.util.ArrayList;
import java.util.List;

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
        @Protobuf(2) public List<T> ts;
        @Protobuf(3) public U[] us;
    }

    @Test
    public void testSchema() throws Exception {
        Assert.assertEquals("enum EnumExample {\n  A = 1;\n  B = 2;\n  C = 3;\n}\nmessage MessageExampleEnumExampleDataExample {\n  optional bytes binary = 1;\n  repeated EnumExample ts = 2 [packed=true];\n  repeated DataExample us = 3;\n}\nmessage DataExample {\n  optional int32 data = 1;\n  optional string description = 2;\n  repeated float floats = 3 [packed=true];\n}\n", protobufObjectMapper.collectTypes(new TypeReference<MessageExample<EnumExample,DataExample>>() {}).getProtobufDefinition());
    }

    @Test
    public void testSerialization() throws Exception {
        MessageExample<EnumExample,DataExample> data = new MessageExample<EnumExample,DataExample>();
        data.binary = new byte[] { 0, 1, 2 };
        data.ts = new ArrayList<EnumExample>();
        data.ts.add(EnumExample.C);
        data.ts.add(EnumExample.A);
        data.us = new DataExample[] { new DataExample() };
        data.us[0].data = 1;
        data.us[0].description = "a";
        data.us[0].floats = new float[] { 0.0f };
        Assert.assertEquals("{\"binary\":\"AAEC\",\"ts\":[\"C\",\"A\"],\"us\":[{\"data\":1,\"description\":\"a\",\"floats\":[0.0]}]}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] {
            0x0a, // length-delimited, field number 1
            0x03, // length=3
            0x00, 0x01, 0x02,
            0x12, // length-delimited, field number 2
            0x02, // length=2
            0x03, // C
            0x01, // A
            0x1a, // length-delimited, field number 3
            0x0b, // length=11
            0x08, // varint, field number 1
            0x01, // 1
            0x12, // length-delimited, field number 2
            0x01, // length=1
            0x61, // "a"
            0x1a, // length-delimited, field number 3
            0x04, // length=4
            0x00, 0x00, 0x00, 0x00, // 0.0f
        }, protobufObjectMapper.writeValueAsBytes(data, new TypeReference<MessageExample<EnumExample,DataExample>>() {}));
    }

    public void testDeserialization() throws Exception {
        MessageExample<EnumExample,DataExample> data = protobufObjectMapper.readValue(new byte[] {
            0x0a, // length-delimited, field number 1
            0x02, // length=2
            0x01, 0x02,
            0x12, // length-delimited, field number 2
            0x01, // length=1
            0x02, // B
            0x1a, // length-delimited, field number 3
            0x13, // length=19
            0x08, // varint, field number 1
            0x03, // 3
            0x12, // length-delimited, field number 2
            0x01, // length=1
            0x62, // "b"
            0x1a, // length-delimited, field number 3
            0x0c, // length=12
            0x00, 0x00, 0x00, 0x00, // 0.0f
            0x00, 0x00, 0x00, 0x00, // 0.0f
            0x00, 0x00, 0x00, 0x00, // 0.0f
        }, new TypeReference<MessageExample<EnumExample,DataExample>>() {});
        Assert.assertArrayEquals(new byte[] { 1, 2 }, data.binary);
        Assert.assertEquals(1, data.ts.size());
        Assert.assertEquals(EnumExample.B, data.ts.get(0));
        Assert.assertEquals(1, data.us.length);
        Assert.assertEquals(3, data.us[0].data);
        Assert.assertEquals("b", data.us[0].description);
        Assert.assertEquals(3, data.us[0].floats.length);
        Assert.assertEquals(0.0, data.us[0].floats[0], Float.MIN_VALUE);
        Assert.assertEquals(0.0, data.us[0].floats[1], Float.MIN_VALUE);
        Assert.assertEquals(0.0, data.us[0].floats[2], Float.MIN_VALUE);
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

    @Test
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

    @Test
    public void testExamplesDeserialization() throws Exception {
        Test1 test1 = protobufObjectMapper.readValue(new byte[] { 0x08, (byte) 0x96, 0x01 }, Test1.class);
        Assert.assertEquals(150, test1.a);

        Test2 test2 = protobufObjectMapper.readValue(new byte[] { 0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6e, 0x67 }, Test2.class);
        Assert.assertEquals("testing", test2.b);

        Test3 test3 = protobufObjectMapper.readValue(new byte[] { 0x1a, 0x03, 0x08, (byte) 0x96, 0x01 }, Test3.class);
        Assert.assertNotNull(test3.c);
        Assert.assertEquals(150, test3.c.a);

        Test4 test4 = protobufObjectMapper.readValue(new byte[] { 0x22, 0x06, 0x03, (byte) 0x8e, 0x02, (byte) 0x9e, (byte) 0xa7, 0x05 }, Test4.class);
        Assert.assertArrayEquals(new int[] { 3, 270, 86942 }, test4.d);
    }

    public static class TestRepeated {
        @Protobuf(1) public String[] a;
        @Protobuf(2) public EnumExample[] b;
        @Protobuf(3) public Test1[] c;
        @Protobuf(4) public boolean d;
    }

    @Test
    public void testRepeated() throws Exception {
        Assert.assertEquals("enum EnumExample {\n  A = 1;\n  B = 2;\n  C = 3;\n}\nmessage Test1 {\n  required int32 a = 1;\n}\nmessage TestRepeated {\n  repeated string a = 1;\n  repeated EnumExample b = 2 [packed=true];\n  repeated Test1 c = 3;\n  optional bool d = 4;\n}\n", protobufObjectMapper.collectTypes(TestRepeated.class).getProtobufDefinition());

        TestRepeated data = new TestRepeated();
        data.a = new String[] { "1" };
        data.b = new EnumExample[] { EnumExample.C, EnumExample.B };
        data.c = new Test1[] { new Test1(), new Test1(), new Test1() };
        data.c[0].a = 5;
        data.c[1].a = 6;
        data.c[2].a = 7;
        data.d = true;
        Assert.assertArrayEquals(new byte[] {
            0x0a, // length-delimited, field number 1
            0x01, // length=1
            0x31, // "1"
            0x12, // length-delimited, field number 2
            0x02, // length=2
            0x03, // 3=EnumExample.C
            0x02, // 2=EnumExample.B
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x05, // 5
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x06, // 6
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x07, // 7
            0x20, // varint, field number 4
            0x01, // true
        }, protobufObjectMapper.writeValueAsBytes(data));

        TestRepeated data2 = protobufObjectMapper.readValue(new byte[] {
            0x0a, // length-delimited, field number 1
            0x01, // length=1
            0x31, // "1"
            0x12, // length-delimited, field number 2
            0x03, // length=3
            0x02, // 2=EnumExample.B
            0x03, // 3=EnumExample.C
            0x01, // 2=EnumExample.A
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x05, // 5
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x06, // 6
            0x1a, // length-delimited, field number 3
            0x02, // length=2
            0x08, // varint, field number 1
            0x07, // 7
            0x20, // varint, field number 4
            0x01, // true
        }, TestRepeated.class);
        Assert.assertArrayEquals(new String[] { "1" }, data2.a);
        Assert.assertArrayEquals(new EnumExample[] { EnumExample.B, EnumExample.C, EnumExample.A }, data2.b);
        Assert.assertNotNull(data2.c);
        Assert.assertEquals(3, data2.c.length);
        Assert.assertEquals(5, data2.c[0].a);
        Assert.assertEquals(6, data2.c[1].a);
        Assert.assertEquals(7, data2.c[2].a);
        Assert.assertEquals(true, data2.d);
    }

    public static class OutOfOrder {
        @Protobuf(16) public String a;
        @Protobuf(value=11, type=Protobuf.Type.SINT32) public int b;
        @Protobuf(10) public boolean c;
    }

    @Test
    public void testOutOfOrder() throws Exception {
        OutOfOrder data = new OutOfOrder();
        data.a = "data";
        data.b = -2;
        data.c = true;
        Assert.assertArrayEquals(new byte[] {
            0x50, 0x1, // varint, field number=10, true
            0x58, 0x3, // varint, field number=11, -2
            (byte) 0x82, 0x1, 0x4, // length-delimited, field number=16, length=4
            0x64, 0x61, 0x74, 0x61, // "data"
        }, protobufObjectMapper.writeValueAsBytes(data));
    }
}
