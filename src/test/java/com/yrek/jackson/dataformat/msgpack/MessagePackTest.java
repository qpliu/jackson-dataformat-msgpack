package com.yrek.jackson.dataformat.msgpack;

import java.util.ArrayList;
import java.util.HashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.junit.Assert;
import org.junit.Test;

public class MessagePackTest {
    private ObjectMapper jsonMapper = new ObjectMapper();
    private ObjectMapper msgPackMapper = new ObjectMapper(new MessagePackFactory());

    @Test
    public void testMessagePack() throws Exception {
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
    public void testMessagePack2() throws Exception {
        Example data = new Example();
        Assert.assertEquals("{\"compact\":true,\"schema\":0}",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00 }, msgPackMapper.writeValueAsBytes(data));
    }

    @Test
    public void testMessagePack3() throws Exception {
        Example[] data = new Example[] { new Example(), null, new Example() };
        data[0].compact = false;
        data[0].schema = 45;
        data[0].bytes = new byte[] { 1, 2, 3, 4, 5, 6 };
        Assert.assertEquals("[{\"compact\":false,\"schema\":45,\"bytes\":\"AQIDBAUG\"},null,{\"compact\":true,\"schema\":0}]",jsonMapper.writeValueAsString(data));
        Assert.assertArrayEquals(new byte[] { (byte) 0x93, (byte) 0x83, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d, (byte) 0xa5, 0x62, 0x79, 0x74, 0x65, 0x73, (byte) 0xa6, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, (byte) 0xc0, (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc3, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x00 }, msgPackMapper.writeValueAsBytes(data));
    }

    @Test
    public void testMessagePack4() throws Exception {
        HashMap map = msgPackMapper.readValue(new byte[] { (byte) 0x82, (byte) 0xa7, 0x63, 0x6f, 0x6d, 0x70, 0x61, 0x63, 0x74, (byte) 0xc2, (byte) 0xa6, 0x73, 0x63, 0x68, 0x65, 0x6d, 0x61, 0x2d }, HashMap.class);
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(Integer.valueOf(45), map.get("schema"));
        Assert.assertEquals(Boolean.FALSE, map.get("compact"));
    }

    @Test
    public void testMessagePack5() throws Exception {
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
}
