package com.yrek.jackson.dataformat.protobuf;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.yrek.jackson.dataformat.msgpack.MessagePackVersion;

public class ProtobufObjectMapper extends ObjectMapper {
    private static final long serialVersionUID = 0L;

    public ProtobufObjectMapper() {
        this(new ProtobufFactory());
    }

    public ProtobufObjectMapper(ProtobufFactory protobufFactory) {
        super(protobufFactory);
    }

    @Override
    public Version version() {
        return MessagePackVersion.VERSION;
    }
    
    /**
     * Actual implementation of value reading+binding operation.
     */
    @Override
    protected Object _readValue(DeserializationConfig cfg, JsonParser jp, JavaType valueType) throws IOException, JsonParseException, JsonMappingException {
        return super._readValue(cfg, setRootContext(jp, valueType), valueType);
    }
    
    @Override
    protected Object _readMapAndClose(JsonParser jp, JavaType valueType) throws IOException, JsonParseException, JsonMappingException {
        return super._readMapAndClose(setRootContext(jp, valueType), valueType);
    }

    /**
     * Method that can be used to serialize any Java value as
     * JSON output, using provided {@link JsonGenerator}.
     */
    @Override
    public void writeValue(JsonGenerator jgen, Object value) throws IOException, JsonGenerationException, JsonMappingException {
        super.writeValue(setRootContext(jgen, value), value);
    }

    /**
     * Method that can be used to serialize any Java value as
     * JSON output, written to File provided.
     */
    @Override
    public void writeValue(File resultFile, Object value) throws IOException, JsonGenerationException, JsonMappingException {
        _configAndWriteValue(setRootContext(_jsonFactory.createGenerator(resultFile, JsonEncoding.UTF8), value), value);
    }

    /**
     * Method that can be used to serialize any Java value as
     * JSON output, using output stream provided (using encoding
     * {@link JsonEncoding#UTF8}).
     *<p>
     * Note: method does not close the underlying stream explicitly
     * here; however, {@link JsonFactory} this mapper uses may choose
     * to close the stream depending on its settings (by default,
     * it will try to close it when {@link JsonGenerator} we construct
     * is closed).
     */
    @Override
    public void writeValue(OutputStream out, Object value) throws IOException, JsonGenerationException, JsonMappingException {
        _configAndWriteValue(setRootContext(_jsonFactory.createGenerator(out, JsonEncoding.UTF8), value), value);
    }

    /**
     * Method that can be used to serialize any Java value as
     * JSON output, using Writer provided.
     *<p>
     * Note: method does not close the underlying stream explicitly
     * here; however, {@link JsonFactory} this mapper uses may choose
     * to close the stream depending on its settings (by default,
     * it will try to close it when {@link JsonGenerator} we construct
     * is closed).
     */
    @Override
    public void writeValue(Writer w, Object value) throws IOException, JsonGenerationException, JsonMappingException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that can be used to serialize any Java value as
     * a String. Functionally equivalent to calling
     * {@link #writeValue(Writer,Object)} with {@link java.io.StringWriter}
     * and constructing String, but more efficient.
     *<p>
     * Note: prior to version 2.1, throws clause included {@link IOException}; 2.1 removed it.
     */
    @Override
    public String writeValueAsString(Object value) throws JsonProcessingException {        
        throw new UnsupportedOperationException();
    }

    /**
     * Method that can be used to serialize any Java value as
     * a byte array. Functionally equivalent to calling
     * {@link #writeValue(Writer,Object)} with {@link java.io.ByteArrayOutputStream}
     * and getting bytes, but more efficient.
     * Encoding used will be UTF-8.
     *<p>
     * Note: prior to version 2.1, throws clause included {@link IOException}; 2.1 removed it.
     */
    @Override
    @SuppressWarnings("resource")
    public byte[] writeValueAsBytes(Object value) throws JsonProcessingException {
        ByteArrayBuilder bb = new ByteArrayBuilder(_jsonFactory._getBufferRecycler());
        try {
            _configAndWriteValue(setRootContext(_jsonFactory.createGenerator(bb, JsonEncoding.UTF8), value), value);
        } catch (JsonProcessingException e) { // to support [JACKSON-758]
            throw e;
        } catch (IOException e) { // shouldn't really happen, but is declared as possibility so:
            throw JsonMappingException.fromUnexpectedIOE(e);
        }
        byte[] result = bb.toByteArray();
        bb.release();
        return result;
    }

    private JsonGenerator setRootContext(JsonGenerator jgen, Object value) throws JsonMappingException {
        if (jgen instanceof ProtobufGenerator) {
            ProtobufSchema schema = collectTypes(value.getClass());
            ((ProtobufGenerator) jgen).setObjectContext(schema.getMessageDescription(getSerializationConfig().constructType(value.getClass())), schema);;
        }
        return jgen;
    }

    private JsonParser setRootContext(JsonParser jp, JavaType javaType) throws JsonMappingException {
        /*
        if (jp instanceof ProtobufParser) {
            ProtobufSchema schema = collectTypes(javaType);
            ((ProtobufParser) jp).setObjectContext(schema.getMessageDescription(javaType), schema);
        }
        */
        return jp;
    }

    public void collectTypes(ProtobufSchema schema, Class<?>... cls) throws JsonMappingException {
        for (Class<?> cl : cls)
            collectType(schema, getSerializationConfig().constructType(cl));
    }

    public ProtobufSchema collectTypes(Class<?>... cls) throws JsonMappingException {
        ProtobufSchema schema = new ProtobufSchema(getSerializationConfig());
        collectTypes(schema, cls);
        return schema;
    }

    public void collectTypes(ProtobufSchema schema, TypeReference<?>... typeReferences) throws JsonMappingException {
        for (TypeReference<?> typeReference : typeReferences)
            collectType(schema, getSerializationConfig().constructType(typeReference));
    }

    public ProtobufSchema collectTypes(TypeReference<?>... typeReferences) throws JsonMappingException {
        ProtobufSchema schema = new ProtobufSchema(getSerializationConfig());
        collectTypes(schema, typeReferences);
        return schema;
    }

    private void collectType(ProtobufSchema schema, JavaType javaType) throws JsonMappingException {
        try {
            schema.collectType(javaType);
        } catch (NoSuchFieldException e) {
            throw new JsonMappingException(e.getMessage(), e);
        }
    }
}
