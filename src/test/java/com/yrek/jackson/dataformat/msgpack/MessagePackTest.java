package com.yrek.jackson.dataformat.msgpack;

import java.util.ArrayList;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.junit.Assert;
import org.junit.Test;

import com.yrek.jackson.dataformat.protobuf.Protobuf;

public class MessagePackTest {
    private ObjectMapper jsonMapper = new ObjectMapper();
    private ObjectMapper msgPackMapper = new MessagePackObjectMapper();

    @Test
    public void testSerializeHashMap() throws Exception {
        HashMap data = new HashMap();
        data.put("compact", true);
        data.put("schema", 0);
        Assert.assertEquals("{\"schema\":0,\"compact\":true}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3 }, msgPackMapper.writeValueAsBytes(data));
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class Example {
        public boolean compact = true;
        public int schema = 0;
        public byte[] bytes = null;
    }

    @Test
    public void testSerializeExample() throws Exception {
        Example data = new Example();
        Assert.assertEquals("{\"compact\":true,\"schema\":0}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00 }, msgPackMapper.writeValueAsBytes(data));
    }

    @Test
    public void testSerializeExample2() throws Exception {
        Example[] data = new Example[] { new Example(), null, new Example() };
        data[0].compact = false;
        data[0].schema = 45;
        data[0].bytes = new byte[] { 1, 2, 3, 4, 5, 6 };
        Assert.assertEquals("[{\"compact\":false,\"schema\":45,\"bytes\":\"AQIDBAUG\"},null,{\"compact\":true,\"schema\":0}]",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x93, (byte) 0x83, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d, (byte) 0xa5, 0x62, 0x79, 0x74, 0x65, 0x73, (byte) 0xa6, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, (byte) 0xc0, (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00 }, msgPackMapper.writeValueAsBytes(data));
    }

    @Test
    public void testDeserializeHashMap() throws Exception {
        HashMap map = msgPackMapper.readValue(new byte[] { (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d }, HashMap.class);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(Integer.valueOf(45), map.get("schema"));
        Assert.assertEquals(Boolean.FALSE, map.get("compact"));
    }

    @Test
    public void testDeserializeExample() throws Exception {
        Example data = msgPackMapper.readValue(new byte[] { (byte) 0x83, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d, (byte) 0xa5, 0x62, 0x79, 0x74, 0x65, 0x73, (byte) 0xa6, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06 }, Example.class);
        Assert.assertEquals(false, data.compact);
        Assert.assertEquals(45, data.schema);
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6 }, data.bytes);
    }

    @Test
    public void testDeserializeExample2() throws Exception {
        Example[] data = msgPackMapper.readValue(new byte[] { (byte) 0x93, (byte) 0x83, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d, (byte) 0xa5, 0x62, 0x79, 0x74, 0x65, 0x73, (byte) 0xa6, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, (byte) 0xc0, (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00 }, Example[].class);
        Assert.assertEquals(3, data.length);
        Assert.assertNotNull(data[0]);
        Assert.assertEquals(false, data[0].compact);
        Assert.assertEquals(45, data[0].schema);
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 6 }, data[0].bytes);
        Assert.assertNull(data[1]);
        Assert.assertNotNull(data[2]);
        Assert.assertEquals(true, data[2].compact);
        Assert.assertEquals(0, data[2].schema);
        Assert.assertNull(data[2].bytes);
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class CompactExample {
        @MessagePack(0)
        public boolean compact = true;
        @MessagePack(1)
        public int schema = 0;
        public byte[] bytes = null;
    }

    @Test
    public void testDeserializeCompactExample() throws Exception {
        CompactExample data = msgPackMapper.readValue(new byte[] { (byte) 0x82, 0x0, (byte) 0xc3, 0x01, 0x40 }, CompactExample.class);
        Assert.assertEquals(true, data.compact);
        Assert.assertEquals(64, data.schema);
        Assert.assertNull(data.bytes);
    }

    @Test
    public void testSerializeCompactExample() throws Exception {
        CompactExample data = new CompactExample();
        data.schema = 13;
        Assert.assertEquals("{\"compact\":true,\"schema\":13}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, 0x0, (byte) 0xc3, 0x01, 0x0d }, msgPackMapper.writeValueAsBytes(data));
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class ContainExample {
        public String name;
        public ContainExample example;
    }

    @Test
    public void testDeserializeContainExample() throws Exception {
        ContainExample data = msgPackMapper.readValue(new byte[] { (byte) 0x81, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e }, ContainExample.class);
        Assert.assertEquals("contain", data.name);
        Assert.assertNull(data.example);

        data = msgPackMapper.readValue(new byte[] { (byte) 0x82, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e, (byte) 0xa7, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, (byte) 0x81, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa3, 0x61, 0x62, 0x63 }, ContainExample.class);
        Assert.assertEquals("contain", data.name);
        Assert.assertNotNull(data.example);
        Assert.assertEquals("abc", data.example.name);
        Assert.assertNull(data.example.example);
    }

    @Test
    public void testSerializeContainExample() throws Exception {
        ContainExample data = new ContainExample();
        data.name = "contain";
        Assert.assertEquals("{\"name\":\"contain\"}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x81, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e }, msgPackMapper.writeValueAsBytes(data));
        data.example = new ContainExample();
        data.example.name = "abc";
        Assert.assertEquals("{\"name\":\"contain\",\"example\":{\"name\":\"abc\"}}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e, (byte) 0xa7, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, (byte) 0x81, (byte) 0xa4, 0x6e, 0x61, 0x6d, 0x65, (byte) 0xa3, 0x61, 0x62, 0x63 }, msgPackMapper.writeValueAsBytes(data));
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class CompactContainExample {
        @MessagePack(0)
        public String name;
        @MessagePack(1)
        public CompactContainExample example;
    }

    @Test
    public void testDeserializeCompactContainExample() throws Exception {
        CompactContainExample data = msgPackMapper.readValue(new byte[] { (byte) 0x81, 0x00, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e }, CompactContainExample.class);
        Assert.assertEquals("contain", data.name);
        Assert.assertNull(data.example);

        data = msgPackMapper.readValue(new byte[] { (byte) 0x82, 0x00, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e, 0x01, (byte) 0x81, 0x00, (byte) 0xa3, 0x61, 0x62, 0x63 }, CompactContainExample.class);
        Assert.assertEquals("contain", data.name);
        Assert.assertNotNull(data.example);
        Assert.assertEquals("abc", data.example.name);
        Assert.assertNull(data.example.example);
    }

    @Test
    public void testSerializeCompactContainExample() throws Exception {
        CompactContainExample data = new CompactContainExample();
        data.name = "contain";
        Assert.assertEquals("{\"name\":\"contain\"}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x81, 0x00, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e }, msgPackMapper.writeValueAsBytes(data));
        data.example = new CompactContainExample();
        data.example.name = "abc";
        Assert.assertEquals("{\"name\":\"contain\",\"example\":{\"name\":\"abc\"}}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, 0x00, (byte) 0xa7, 0x63, 0x6f, 0x6e, 0x74, 0x61, 0x69, 0x6e, 0x01, (byte) 0x81, 0x00, (byte) 0xa3, 0x61, 0x62, 0x63 }, msgPackMapper.writeValueAsBytes(data));
    }

    public enum EnumExample {
        one, two, three
    }

    @Test
    public void testSerializeEnum() throws Exception {
        Assert.assertEquals("\"two\"", jsonMapper.writeValueAsString(EnumExample.two));
        Assert.assertArrayEquals(new byte[] { (byte) 0xa3, 0x74, 0x77, 0x6f }, msgPackMapper.writeValueAsBytes(EnumExample.two));
    }

    @Test
    public void testDeserializeEnum() throws Exception {
        EnumExample data = msgPackMapper.readValue(new byte[] { (byte) 0xa3, 0x74, 0x77, 0x6f }, EnumExample.class);
        Assert.assertEquals(EnumExample.two, data);
    }

    public enum CompactEnumExample {
        @MessagePack(1)
        uno,
        @MessagePack(2)
        dos,
        @MessagePack(3)
        tres,
    }

    @Test
    public void testSerializeCompactEnum() throws Exception {
        Assert.assertEquals("\"tres\"", jsonMapper.writeValueAsString(CompactEnumExample.tres));
        Assert.assertArrayEquals(new byte[] { 0x03 }, msgPackMapper.writeValueAsBytes(CompactEnumExample.tres));
    }

    @Test
    public void testDeserializeCompactEnum() throws Exception {
        CompactEnumExample data = msgPackMapper.readValue(new byte[] { 0x03 }, CompactEnumExample.class);
        Assert.assertEquals(CompactEnumExample.tres, data);
    }

    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    public static class ProtobufExample {
        @Protobuf(1)
        public String name;
        @Protobuf(2)
        public CompactEnumExample value;
    }

    @Test
    public void testProtobufExample() throws Exception {
        ProtobufExample data = new ProtobufExample();
        data.name = "proto";
        data.value = CompactEnumExample.dos;
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, 0x01, (byte) 0xa5, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x02, 0x02 }, msgPackMapper.writeValueAsBytes(data));
        data = msgPackMapper.readValue(new byte[] { (byte) 0x82, 0x01, (byte) 0xa3, 0x6d, 0x73, 0x67, 0x02, 0x03 }, ProtobufExample.class);
        Assert.assertEquals("msg", data.name);
        Assert.assertEquals(CompactEnumExample.tres, data.value);
    }
}
