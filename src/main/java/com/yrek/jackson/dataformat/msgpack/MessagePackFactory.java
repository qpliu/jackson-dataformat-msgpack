package com.yrek.jackson.dataformat.msgpack;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;

public class MessagePackFactory extends JsonFactory {
    private static final long serialVersionUID = 0L;

    public static final String FORMAT_NAME = "MessagePack";

    public enum Feature implements MessagePackFeature.Feature {
        /**
         *
         */
        USE_ANNOTATIONS(true),
        /**
         *
         */
        FAIL_ON_BIG_NUMBERS(false),
            ;

        private final boolean _defaultState;
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
        }

        @Override
        public boolean enabledByDefault() {
            return _defaultState;
        }
    }

    private EnumSet<Feature> _msgPackFeatures = MessagePackFeature.defaults(Feature.class);
    private EnumSet<MessagePackParser.Feature> _parserFeatures = MessagePackFeature.defaults(MessagePackParser.Feature.class);
    private EnumSet<MessagePackGenerator.Feature> _generatorFeatures = MessagePackFeature.defaults(MessagePackGenerator.Feature.class);
    private ObjectCodec _objectCodec;

    public MessagePackFactory() {
        this(null);
    }

    public MessagePackFactory(ObjectCodec oc) {
        _objectCodec = oc;
    }

    public MessagePackFactory configure(Feature f, boolean state) {
        if (state)
            return enable(f);
        else
            return disable(f);
    }

    public MessagePackFactory enable(Feature f) {
        _msgPackFeatures.add(f);
        return this;
    }

    public MessagePackFactory disable(Feature f) {
        _msgPackFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(Feature f) {
        return _msgPackFeatures.contains(f);
    }

    public MessagePackFactory configure(MessagePackParser.Feature f, boolean state) {
        if (state)
            return enable(f);
        else
            return disable(f);
    }

    public MessagePackFactory enable(MessagePackParser.Feature f) {
        _parserFeatures.add(f);
        return this;
    }

    public MessagePackFactory disable(MessagePackParser.Feature f) {
        _parserFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(MessagePackParser.Feature f) {
        return _parserFeatures.contains(f);
    }

    public MessagePackFactory configure(MessagePackGenerator.Feature f, boolean state) {
        if (state)
            return enable(f);
        else
            return disable(f);
    }

    public MessagePackFactory enable(MessagePackGenerator.Feature f) {
        _generatorFeatures.add(f);
        return this;
    }

    public MessagePackFactory disable(MessagePackGenerator.Feature f) {
        _generatorFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(MessagePackGenerator.Feature f) {
        return _generatorFeatures.contains(f);
    }

    @Override
    public String getFormatName() {
        return FORMAT_NAME;
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonParser _createParser(InputStream in, IOContext ctxt) throws IOException, JsonParseException {
        return new MessagePackParser(ctxt, _objectCodec, EnumSet.copyOf(_msgPackFeatures), EnumSet.copyOf(_parserFeatures), in);
    }

    @Override
    protected JsonParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException, JsonParseException {
        return _createParser(new ByteArrayInputStream(data, offset, len), ctxt);
    }

    @Override
    protected JsonGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonGenerator _createUTF8Generator(OutputStream out, IOContext ctxt) throws IOException {
        return new MessagePackGenerator(ctxt, _objectCodec, EnumSet.copyOf(_msgPackFeatures), EnumSet.copyOf(_generatorFeatures), out);
    }
}
