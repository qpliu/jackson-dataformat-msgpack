package com.yrek.jackson.dataformat.msgpack;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessagePackObjectMapper extends ObjectMapper {
    private static final long serialVersionUID = 0L;

    public MessagePackObjectMapper() {
        this(new MessagePackFactory());
    }

    public MessagePackObjectMapper(MessagePackFactory messagePackFactory) {
        super(messagePackFactory);
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
        if (jp instanceof MessagePackParser)
            ((MessagePackParser) jp).setObjectContext(valueType, new IntrospectionResults(cfg));
        return super._readValue(cfg, jp, valueType);
    }
    
    @Override
    protected Object _readMapAndClose(JsonParser jp, JavaType valueType) throws IOException, JsonParseException, JsonMappingException {
        //... ought to cache the annotation introspection results in MessagePackObjectMapper
        if (jp instanceof MessagePackParser)
            ((MessagePackParser) jp).setObjectContext(valueType, new IntrospectionResults(getDeserializationConfig()));
        return super._readMapAndClose(jp, valueType);
    }
}
