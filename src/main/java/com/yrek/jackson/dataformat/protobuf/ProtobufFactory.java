package com.yrek.jackson.dataformat.protobuf;

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

public class ProtobufFactory extends JsonFactory {
    private ObjectCodec objectCodec;

    public ProtobufFactory() {
        this(null);
    }

    public ProtobufFactory(ObjectCodec objectCodec) {
        this.objectCodec = objectCodec;
    }

    @Override
    public String getFormatName() {
        return "Protobuf";
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonParser _createParser(InputStream in, IOContext ctxt) throws IOException, JsonParseException {
        return null; //...
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
        return null; //...
    }
}
