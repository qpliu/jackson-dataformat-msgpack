package com.yrek.jackson.dataformat.msgpack;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

public class MessagePackBeanSerializerModifier extends BeanSerializerModifier {
    /**
     * Method called by {@link BeanSerializerFactory} after constructing default
     * bean serializer instance with properties collected and ordered earlier.
     * Implementations can modify or replace given serializer and return serializer
     * to use. Note that although initial serializer being passed is of type
     * {@link BeanSerializer}, modifiers may return serializers of other types;
     * and this is why implementations must check for type before casting.
     *<p>
     * NOTE: since 2.2, gets called for serializer of those non-POJO types that
     * do not go through any of more specific <code>modifyXxxSerializer</code>
     * methods; mostly for JDK types like {@link java.util.Iterator} and such.
     */
    @Override
    @SuppressWarnings("unchecked")
    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc, final JsonSerializer<?> serializer) {
        return new ModifiedJsonSerializer(beanDesc.getType(), serializer);
    }

    private static class ModifiedJsonSerializer<T> extends JsonSerializer<T> {
        private JavaType _javaType;
        private JsonSerializer<T> _jsonSerializer;

        ModifiedJsonSerializer(JavaType javaType, JsonSerializer<T> jsonSerializer) {
            _javaType = javaType;
            _jsonSerializer = jsonSerializer;
        }
    
        /*
        /**********************************************************
        /* Serialization methods
        /**********************************************************
         */

        @Override
        public void serialize(T value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            if (jgen instanceof MessagePackGenerator)
                ((MessagePackGenerator) jgen).setObjectContext(_javaType);
            _jsonSerializer.serialize(value, jgen, provider);
        }

        public void serializeWithType(T value, JsonGenerator jgen, SerializerProvider provider, TypeSerializer typeSer) throws IOException, JsonProcessingException {
            if (jgen instanceof MessagePackGenerator)
                ((MessagePackGenerator) jgen).setObjectContext(_javaType);
            _jsonSerializer.serializeWithType(value, jgen, provider, typeSer);
        }
    
        /*
        /**********************************************************
        /* Other accessors
        /**********************************************************
         */
    
        @Override
        public Class<T> handledType() {
            return _jsonSerializer.handledType();
        }

        @Override
        public boolean isEmpty(T value) {
            return _jsonSerializer.isEmpty(value);
        }

        @Override
        public boolean usesObjectId() {
            return _jsonSerializer.usesObjectId();
        }

        @Override
        public boolean isUnwrappingSerializer() {
            return _jsonSerializer.isUnwrappingSerializer();
        }
    
        public JsonSerializer<?> getDelegatee() {
            return _jsonSerializer;
        }

        /*
        /**********************************************************
        /* Default JsonFormatVisitable implementation
        /**********************************************************
         */

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) throws JsonMappingException {
            _jsonSerializer.acceptJsonFormatVisitor(visitor, type);
        }
    }
}
