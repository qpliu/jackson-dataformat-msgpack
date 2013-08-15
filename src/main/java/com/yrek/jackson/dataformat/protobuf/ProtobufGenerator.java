package com.yrek.jackson.dataformat.protobuf;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.databind.JavaType;

import com.yrek.jackson.dataformat.msgpack.MessagePackVersion;

public class ProtobufGenerator extends JsonGenerator {
    private class OutputContext extends JsonStreamContext {
        private final OutputStream out;
        protected final OutputContext parent;
        private final MessageDescription saveObjectContext;
        private final MessageField saveFieldContext;
        private String currentName;

        OutputContext(OutputStream out) {
            this(out, null, TYPE_ROOT);
        }

        OutputContext(OutputStream out, OutputContext parent, int type) {
            this.out = out;
            this.parent = parent;
            this.saveObjectContext = objectContext;
            this.saveFieldContext = fieldContext;
            _type = type;
            _index = -1;
        }

        @Override
        public JsonStreamContext getParent() {
            return parent;
        }

        public void setCurrentName(String currentName) throws JsonGenerationException {
            this.currentName = currentName;
        }

        @Override
        public String getCurrentName() {
            return currentName;
        }

        public OutputStream out() {
            return out;
        }

        public void startElement() throws IOException {
            _index++;
        }

        public void writeKey(WireType wireType) throws IOException {
            varint(wireType.getKey(fieldContext.getTag()));
        }

        public void endElement() throws IOException {
        }

        public OutputContext endContext() throws IOException {
            objectContext = saveObjectContext;
            fieldContext = saveFieldContext;
            return parent;
        }

        public void endParentElement() throws IOException {
            parent.endElement();
        }

        public int zigzag(int i) {
            return (i << 1) ^ (i >> 31);
        }

        public long zigzag(long i) {
            return (i << 1) ^ (i >> 63);
        }

        public void varint(int i) throws IOException {
            for (;;) {
                int b128 = i&127;
                i >>>= 7;
                if (i != 0)
                    b128 |= 128;
                out.write(b128);
                if (i == 0)
                    break;
            }
        }

        public void svarint(int i) throws IOException {
            varint(zigzag(i));
        }

        public void varint(long i) throws IOException {
            for (;;) {
                int b128 = (int) (i&127);
                i >>>= 7;
                if (i != 0)
                    b128 |= 128;
                out.write(b128);
                if (i == 0)
                    break;
            }
        }

        public void svarint(long i) throws IOException {
            varint(zigzag(i));
        }

        public void fixed32(int i32) throws IOException {
            out().write(i32&255);
            out().write((i32>>8)&255);
            out().write((i32>>16)&255);
            out().write((i32>>24)&255);
        }

        public void sfixed32(int i32) throws IOException {
            fixed32(zigzag(i32));
        }

        public void fixed32(float f) throws IOException {
            fixed32(Float.floatToIntBits(f));
        }

        public void fixed64(long i64) throws IOException {
            out().write((int) i64&255);
            out().write((int) (i64>>8)&255);
            out().write((int) (i64>>16)&255);
            out().write((int) (i64>>24)&255);
            out().write((int) (i64>>32)&255);
            out().write((int) (i64>>40)&255);
            out().write((int) (i64>>48)&255);
            out().write((int) (i64>>56)&255);
        }

        public void sfixed64(long i64) throws IOException {
            fixed64(zigzag(i64));
        }

        public void fixed64(double d) throws IOException {
            fixed64(Double.doubleToLongBits(d));
        }
    }

    private static final OutputStream nullOutputStream = new OutputStream() {
        public void write(int i) {}
        public void write(byte[] b) {}
        public void write(byte[] b, int i) {}
        public void write(byte[] b, int offset, int count) {}
        public void flush() {}
        public void close() {}
    };

    private class IgnoredContext extends OutputContext {
        IgnoredContext(OutputContext parent, boolean isObject) {
            super(nullOutputStream, parent, isObject ? TYPE_OBJECT : TYPE_ARRAY);
        }

        @Override
        public void writeKey(WireType wireType) throws IOException {
        }

        @Override
        public void endParentElement() throws IOException {
        }
    }

    private class RootOutputContext extends OutputContext {
        RootOutputContext(OutputContext parent) {
            super(parent.out(), parent, TYPE_OBJECT);
        }
    }

    private class RepeatedOutputContext extends OutputContext {
        RepeatedOutputContext(OutputContext parent) {
            super(parent.out(), parent, TYPE_ARRAY);
        }
    }

    private class LengthDelimitedOutputContext extends OutputContext {
        LengthDelimitedOutputContext(OutputContext parent, boolean isObject) {
            super(new ByteArrayOutputStream(), parent, isObject ? TYPE_OBJECT : TYPE_ARRAY);
        }

        @Override
        public OutputContext endContext() throws IOException {
            ByteArrayOutputStream out = (ByteArrayOutputStream) out();
            parent.varint(out.size());
            out.writeTo(parent.out());
            return super.endContext();
        }
    }

    private class PackedOutputContext extends LengthDelimitedOutputContext {
        PackedOutputContext(OutputContext parent) {
            super(parent, false);
        }

        @Override
        public void writeKey(WireType wireType) throws IOException {
            switch (wireType) {
            case Varint: case Fixed64: case Fixed32:
                break;
            default:
                throw new IllegalArgumentException();
            }
        }
    }

    private IOContext ioContext;
    private ObjectCodec objectCodec;
    private OutputStream outputStream;

    private OutputContext outputContext;
    private MessageDescription objectContext;
    private MessageField fieldContext;
    private ProtobufSchema schema;
    private boolean closed;

    public ProtobufGenerator(IOContext ioContext, ObjectCodec objectCodec, OutputStream outputStream) {
        this.ioContext = ioContext;
        this.objectCodec = objectCodec;
        this.outputStream = outputStream;

        this.outputContext = new OutputContext(outputStream);
    }

    void setObjectContext(MessageDescription objectContext, ProtobufSchema schema) {
        this.objectContext = objectContext;
        this.schema = schema;
    }

    void setObjectContext(MessageDescription objectContext) {
        this.objectContext = objectContext;
    }

    /**
     * Method for enabling specified parser features:
     * check {@link Feature} for list of available features.
     *
     * @return Generator itself (this), to allow chaining
     *
     * @since 1.2
     */
    @Override
    public JsonGenerator enable(JsonGenerator.Feature f) {
        return this;
    }

    /**
     * Method for disabling specified  features
     * (check {@link Feature} for list of features)
     *
     * @return Generator itself (this), to allow chaining
     *
     * @since 1.2
     */
    @Override
    public JsonGenerator disable(JsonGenerator.Feature f) {
        return this;
    }

    /**
     * Method for checking whether given feature is enabled.
     * Check {@link Feature} for list of available features.
     *
     * @since 1.2
     */
    @Override
    public boolean isEnabled(JsonGenerator.Feature f) {
        return false;
    }

    /**
     * Method that can be called to set or reset the object to
     * use for writing Java objects as JsonContent
     * (using method {@link #writeObject}).
     *
     * @return Generator itself (this), to allow chaining
     */
    @Override
    public JsonGenerator setCodec(ObjectCodec objectCodec) {
        this.objectCodec = objectCodec;
        return this;
    }

    /**
     * Method for accessing the object used for writing Java
     * object as Json content
     * (using method {@link #writeObject}).
     */
    @Override
    public ObjectCodec getCodec() {
        return objectCodec;
    }

    /**
     * Accessor for finding out version of the bundle that provided this generator instance.
     */
    @Override
    public Version version() {
        return MessagePackVersion.VERSION;
    }

    /**
     * Convenience method for enabling pretty-printing using
     * the default pretty printer
     * ({@link com.fasterxml.jackson.core.util.DefaultPrettyPrinter}).
     *
     * @return Generator itself (this), to allow chaining
     */
    @Override
    public JsonGenerator useDefaultPrettyPrinter() {
        return this;
    }

    /**
     * Method for writing starting marker of a JSON Array value
     * (character '['; plus possible white space decoration
     * if pretty-printing is enabled).
     *<p>
     * Array values can be written in any context where values
     * are allowed: meaning everywhere except for when
     * a field name is expected.
     */
    @Override
    public void writeStartArray() throws IOException, JsonGenerationException {
        if (fieldContext == null) {
            outputContext = new IgnoredContext(outputContext, false);
            return;
        }
        if (fieldContext.isPacked()) {
            outputContext.startElement();
            outputContext.writeKey(WireType.LengthDelimited);
            outputContext = new PackedOutputContext(outputContext);
            return;
        }
        outputContext = new RepeatedOutputContext(outputContext);
    }

    /**
     * Method for writing closing marker of a JSON Array value
     * (character ']'; plus possible white space decoration
     * if pretty-printing is enabled).
     *<p>
     * Marker can be written if the innermost structured type
     * is Array.
     */
    @Override
    public void writeEndArray() throws IOException, JsonGenerationException {
        OutputContext childContext = outputContext;
        outputContext = outputContext.endContext();
        childContext.endParentElement();
    }

    /**
     * Method for writing starting marker of a JSON Object value
     * (character '{'; plus possible white space decoration
     * if pretty-printing is enabled).
     *<p>
     * Object values can be written in any context where values
     * are allowed: meaning everywhere except for when
     * a field name is expected.
     */
    @Override
    public void writeStartObject() throws IOException, JsonGenerationException {
        if (outputContext.inRoot()) {
            outputContext = new RootOutputContext(outputContext);
            return;
        }
        if (fieldContext == null) {
            outputContext = new IgnoredContext(outputContext, true);
            return;
        }
        MessageDescription newObjectContext = schema.getMessageDescription(fieldContext);
        if (newObjectContext == null) {
            outputContext = new IgnoredContext(outputContext, true);
            return;
        }
        outputContext.startElement();
        outputContext.writeKey(WireType.LengthDelimited);
        outputContext = new LengthDelimitedOutputContext(outputContext, true);
        objectContext = newObjectContext;
    }

    /**
     * Method for writing closing marker of a JSON Object value
     * (character '}'; plus possible white space decoration
     * if pretty-printing is enabled).
     *<p>
     * Marker can be written if the innermost structured type
     * is Object, and the last written event was either a
     * complete value, or START-OBJECT marker (see JSON specification
     * for more details).
     */
    @Override
    public void writeEndObject() throws IOException, JsonGenerationException {
        OutputContext childContext = outputContext;
        outputContext = outputContext.endContext();
        childContext.endParentElement();
    }

    /**
     * Method for writing a field name (JSON String surrounded by
     * double quotes: syntactically identical to a JSON String value),
     * possibly decorated by white space if pretty-printing is enabled.
     *<p>
     * Field names can only be written in Object context (check out
     * JSON specification for details), when field name is expected
     * (field names alternate with values).
     */
    @Override
    public void writeFieldName(String name) throws IOException, JsonGenerationException {
        fieldContext = objectContext.getMessageField(name);
        outputContext.setCurrentName(name);
    }

    /**
     * Method similar to {@link #writeFieldName(String)}, main difference
     * being that it may perform better as some of processing (such as
     * quoting of certain characters, or encoding into external encoding
     * if supported by generator) can be done just once and reused for
     * later calls.
     *<p>
     * Default implementation simple uses unprocessed name container in
     * serialized String; implementations are strongly encouraged to make
     * use of more efficient methods argument object has.
     */
    @Override
    public void writeFieldName(SerializableString name) throws IOException, JsonGenerationException {
        writeFieldName(name.getValue());
    }

    /**
     * Method for outputting a String value. Depending on context
     * this means either array element, (object) field value or
     * a stand alone String; but in all cases, String will be
     * surrounded in double quotes, and contents will be properly
     * escaped as required by JSON specification.
     */
    @Override
    public void writeString(String text) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        EnumDescription enumDescription;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
            enumDescription = schema.getEnumDescription(fieldContext);
            if (enumDescription != null) {
                Integer value = enumDescription.getValue(text);
                if (value != null) {
                    outputContext.startElement();
                    outputContext.writeKey(WireType.Varint);
                    outputContext.varint(value);
                    outputContext.endElement();
                }
                return;
            }
            /*FALLTHROUGH*/
        case STRING:
        case BYTES:
            byte[] bytes = text.getBytes("UTF-8");
            outputContext.startElement();
            outputContext.writeKey(WireType.LengthDelimited);
            outputContext.varint(bytes.length);
            outputContext.out().write(bytes);
            outputContext.endElement();
            break;
        default:
            //... automatic conversions...
        }
    }

    /**
     * Method for outputting a String value. Depending on context
     * this means either array element, (object) field value or
     * a stand alone String; but in all cases, String will be
     * surrounded in double quotes, and contents will be properly
     * escaped as required by JSON specification.
     */
    @Override
    public void writeString(char[] text, int offset, int len) throws IOException, JsonGenerationException {
        writeString(new String(text, offset, len));
    }

    /**
     * Method similar to {@link #writeString(String)}, but that takes
     * {@link SerializableString} which can make this potentially
     * more efficient to call as generator may be able to reuse
     * quoted and/or encoded representation.
     *<p>
     * Default implementation just calls {@link #writeString(String)};
     * sub-classes should override it with more efficient implementation
     * if possible.
     */
    public void writeString(SerializableString text) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        EnumDescription enumDescription;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
            enumDescription = schema.getEnumDescription(fieldContext);
            if (enumDescription != null) {
                Integer value = enumDescription.getValue(text.getValue());
                if (value != null) {
                    outputContext.startElement();
                    outputContext.writeKey(WireType.Varint);
                    outputContext.varint(value);
                    outputContext.endElement();
                }
                return;
            }
            /*FALLTHROUGH*/
        case STRING:
        case BYTES:
            byte[] bytes = text.asUnquotedUTF8();
            outputContext.startElement();
            outputContext.writeKey(WireType.LengthDelimited);
            outputContext.varint(bytes.length);
            outputContext.out().write(bytes);
            outputContext.endElement();
            break;
        default:
            //... automatic conversions...
        }
    }

    /**
     * Method similar to {@link #writeString(String)} but that takes as
     * its input a UTF-8 encoded String that is to be output as-is, without additional
     * escaping (type of which depends on data format; backslashes for JSON).
     * However, quoting that data format requires (like double-quotes for JSON) will be added
     * around the value if and as necessary.
     *<p>
     * Note that some backends may choose not to support this method: for
     * example, if underlying destination is a {@link java.io.Writer}
     * using this method would require UTF-8 decoding.
     * If so, implementation may instead choose to throw a
     * {@link UnsupportedOperationException} due to ineffectiveness
     * of having to decode input.
     * 
     * @since 1.7
     */
    @Override
    public void writeRawUTF8String(byte[] text, int offset, int length) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method similar to {@link #writeString(String)} but that takes as its input
     * a UTF-8 encoded String which has <b>not</b> been escaped using whatever
     * escaping scheme data format requires (for JSON that is backslash-escaping
     * for control characters and double-quotes; for other formats something else).
     * This means that textual JSON backends need to check if value needs
     * JSON escaping, but otherwise can just be copied as is to output.
     * Also, quoting that data format requires (like double-quotes for JSON) will be added
     * around the value if and as necessary.
     *<p>
     * Note that some backends may choose not to support this method: for
     * example, if underlying destination is a {@link java.io.Writer}
     * using this method would require UTF-8 decoding.
     * In this case
     * generator implementation may instead choose to throw a
     * {@link UnsupportedOperationException} due to ineffectiveness
     * of having to decode input.
     * 
     * @since 1.7
     */
    @Override
    public void writeUTF8String(byte[] text, int offset, int length) throws IOException, JsonGenerationException {
        writeBinary(null, text, offset, length);
    }

    /**
     * Method that will force generator to copy
     * input text verbatim with <b>no</b> modifications (including
     * that no escaping is done and no separators are added even
     * if context [array, object] would otherwise require such).
     * If such separators are desired, use
     * {@link #writeRawValue(String)} instead.
     *<p>
     * Note that not all generator implementations necessarily support
     * such by-pass methods: those that do not will throw
     * {@link UnsupportedOperationException}.
     */
    @Override
    public void writeRaw(String text) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that will force generator to copy
     * input text verbatim with <b>no</b> modifications (including
     * that no escaping is done and no separators are added even
     * if context [array, object] would otherwise require such).
     * If such separators are desired, use
     * {@link #writeRawValue(String)} instead.
     *<p>
     * Note that not all generator implementations necessarily support
     * such by-pass methods: those that do not will throw
     * {@link UnsupportedOperationException}.
     */
    @Override
    public void writeRaw(String text, int offset, int len) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that will force generator to copy
     * input text verbatim with <b>no</b> modifications (including
     * that no escaping is done and no separators are added even
     * if context [array, object] would otherwise require such).
     * If such separators are desired, use
     * {@link #writeRawValue(String)} instead.
     *<p>
     * Note that not all generator implementations necessarily support
     * such by-pass methods: those that do not will throw
     * {@link UnsupportedOperationException}.
     */
    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that will force generator to copy
     * input text verbatim with <b>no</b> modifications (including
     * that no escaping is done and no separators are added even
     * if context [array, object] would otherwise require such).
     * If such separators are desired, use
     * {@link #writeRawValue(String)} instead.
     *<p>
     * Note that not all generator implementations necessarily support
     * such by-pass methods: those that do not will throw
     * {@link UnsupportedOperationException}.
     */
    @Override
    public void writeRaw(char c) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that will force generator to copy
     * input text verbatim without any modifications, but assuming
     * it must constitute a single legal JSON value (number, string,
     * boolean, null, Array or List). Assuming this, proper separators
     * are added if and as needed (comma or colon), and generator
     * state updated to reflect this.
     */
    @Override
    public void writeRawValue(String text) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that will output given chunk of binary data as base64
     * encoded, as a complete String value (surrounded by double quotes).
     * This method defaults
     *<p>
     * Note: because Json Strings can not contain unescaped linefeeds,
     * if linefeeds are included (as per last argument), they must be
     * escaped. This adds overhead for decoding without improving
     * readability.
     * Alternatively if linefeeds are not included,
     * resulting String value may violate the requirement of base64
     * RFC which mandates line-length of 76 characters and use of
     * linefeeds. However, all {@link JsonParser} implementations
     * are required to accept such "long line base64"; as do
     * typical production-level base64 decoders.
     *
     * @param b64variant Base64 variant to use: defines details such as
     *   whether padding is used (and if so, using which character);
     *   what is the maximum line length before adding linefeed,
     *   and also the underlying alphabet to use.
     */
    @Override
    public void writeBinary(Base64Variant b64variant, byte[] data, int offset, int len) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
        case BYTES:
            outputContext.startElement();
            outputContext.writeKey(WireType.LengthDelimited);
            outputContext.varint(len);
            outputContext.out().write(data, offset, len);
            outputContext.endElement();
            break;
        }
    }

    /**
     * Method similar to {@link #writeBinary(Base64Variant,byte[],int,int)},
     * but where input is provided through a stream, allowing for incremental
     * writes without holding the whole input in memory.
     * 
     * @param b64variant Base64 variant to use
     * @param data InputStream to use for reading binary data to write.
     *    Will not be closed after successful write operation
     * @param dataLength (optional) number of bytes that will be available;
     *    or -1 to be indicate it is not known.
     *    If a positive length is given, <code>data</code> MUST provide at least
     *    that many bytes: if not, an exception will be thrown.
     *    Note that implementations
     *    need not support cases where length is not known in advance; this
     *    depends on underlying data format: JSON output does NOT require length,
     *    other formats may.
     * 
     * @return Number of bytes read from <code>data</code> and written as binary payload
     * 
     * @since 2.1
     */
    @Override
    public int writeBinary(Base64Variant b64variant, InputStream data, int dataLength) throws IOException, JsonGenerationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method for outputting given value as Json number.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(int v) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
        case INT32: case INT64: case UINT32: case UINT64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Varint);
            outputContext.varint(v);
            outputContext.endElement();
            break;
        case SINT32: case SINT64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Varint);
            outputContext.svarint(v);
            outputContext.endElement();
            break;
        case FIXED32:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed32);
            outputContext.fixed32(v);
            outputContext.endElement();
            break;
        case FIXED64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.fixed64(v);
            outputContext.endElement();
            break;
        case SFIXED32:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed32);
            outputContext.sfixed32(v);
            outputContext.endElement();
            break;
        case SFIXED64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.sfixed64(v);
            outputContext.endElement();
            break;
        }
    }

    /**
     * Method for outputting given value as Json number.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(long v) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
        case INT64: case UINT64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Varint);
            outputContext.varint(v);
            outputContext.endElement();
            break;
        case SINT64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Varint);
            outputContext.svarint(v);
            outputContext.endElement();
            break;
        case FIXED64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.fixed64(v);
            outputContext.endElement();
            break;
        case SFIXED64:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.sfixed64(v);
            outputContext.endElement();
            break;
        case INT32: case SINT32: case FIXED32: case SFIXED32:
            writeNumber((int) v);
            break;
        }
    }

    /**
     * Method for outputting given value as Json number.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(BigInteger v) throws IOException, JsonGenerationException {
        //...
        throw new UnsupportedOperationException();
    }

    /**
     * Method for outputting indicate Json numeric value.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(double d) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT: case DOUBLE:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.fixed64(d);
            outputContext.endElement();
            break;
        case FLOAT:
            writeNumber((float) d);
            break;
        case INT32: case INT64:
        case UINT32: case UINT64:
        case SINT32: case SINT64:
        case FIXED32: case FIXED64:
        case SFIXED32: case SFIXED64:
            writeNumber((long) d);
            break;
        }
    }

    /**
     * Method for outputting indicate Json numeric value.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(float f) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
        case FLOAT:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed32);
            outputContext.fixed32(f);
            outputContext.endElement();
            break;
        case DOUBLE:
            outputContext.startElement();
            outputContext.writeKey(WireType.Fixed64);
            outputContext.fixed64((double) f);
            outputContext.endElement();
            break;
        case INT32: case INT64:
        case UINT32: case UINT64:
        case SINT32: case SINT64:
        case FIXED32: case FIXED64:
        case SFIXED32: case SFIXED64:
            writeNumber((long) f);
            break;
        }
    }

    /**
     * Method for outputting indicate Json numeric value.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNumber(BigDecimal dec) throws IOException, JsonGenerationException {
        //...
        throw new UnsupportedOperationException();
    }

    /**
     * Write method that can be used for custom numeric types that can
     * not be (easily?) converted to "standard" Java number types.
     * Because numbers are not surrounded by double quotes, regular
     * {@link #writeString} method can not be used; nor
     * {@link #writeRaw} because that does not properly handle
     * value separators needed in Array or Object contexts.
     *<p>
     * Note: because of lack of type safety, some generator
     * implementations may not be able to implement this
     * method. For example, if a binary json format is used,
     * it may require type information for encoding; similarly
     * for generator-wrappers around Java objects or Json nodes.
     * If implementation does not implement this method,
     * it needs to throw {@link UnsupportedOperationException}.
     */
    @Override
    public void writeNumber(String encodedValue) throws IOException, JsonGenerationException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method for outputting literal Json boolean value (one of
     * Strings 'true' and 'false').
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeBoolean(boolean state) throws IOException, JsonGenerationException {
        if (fieldContext == null)
            return;
        switch (fieldContext.getProtobufType()) {
        case DEFAULT:
            outputContext.startElement();
            outputContext.writeKey(WireType.Varint);
            outputContext.out().write(state ? 1 : 0);
            outputContext.endElement();
            break;
        }
    }

    /**
     * Method for outputting literal Json null value.
     * Can be called in any context where a value is expected
     * (Array value, Object field value, root-level value).
     * Additional white space may be added around the value
     * if pretty-printing is enabled.
     */
    @Override
    public void writeNull() throws IOException, JsonGenerationException {
    }

    /**
     * Method for writing given Java object (POJO) as Json.
     * Exactly how the object gets written depends on object
     * in question (ad on codec, its configuration); for most
     * beans it will result in Json object, but for others Json
     * array, or String or numeric value (and for nulls, Json
     * null literal.
     * <b>NOTE</b>: generator must have its <b>object codec</b>
     * set to non-null value; for generators created by a mapping
     * factory this is the case, for others not.
     */
    @Override
    public void writeObject(Object pojo) throws IOException, JsonProcessingException {
        if (pojo == null) {
            writeNull();
        } else {
            objectCodec.writeValue(this, pojo);
        }
    }

    /**
     * Method for writing given JSON tree (expressed as a tree
     * where given JsonNode is the root) using this generator.
     * This will generally just call
     * {@link #writeObject} with given node, but is added
     * for convenience and to make code more explicit in cases
     * where it deals specifically with trees.
     */
    @Override
    public void writeTree(TreeNode rootNode) throws IOException, JsonProcessingException {
        writeObject(rootNode);
    }

    /**
     * Method for copying contents of the current event that
     * the given parser instance points to.
     * Note that the method <b>will not</b> copy any other events,
     * such as events contained within Json Array or Object structures.
     *<p>
     * Calling this method will not advance the given
     * parser, although it may cause parser to internally process
     * more data (if it lazy loads contents of value events, for example)
     */
    @Override
    public void copyCurrentEvent(JsonParser jp) throws IOException, JsonProcessingException {
        switch(jp.getCurrentToken()) {
        case START_OBJECT:
            writeStartObject();
            break;
        case END_OBJECT:
            writeEndObject();
            break;
        case START_ARRAY:
            writeStartArray();
            break;
        case END_ARRAY:
            writeEndArray();
            break;
        case FIELD_NAME:
            writeFieldName(jp.getCurrentName());
            break;
        case VALUE_STRING:
            writeString(jp.getText());
            break;
        case VALUE_NUMBER_INT:
            switch (jp.getNumberType()) {
            case INT:
                writeNumber(jp.getIntValue());
                break;
            case BIG_INTEGER:
                writeNumber(jp.getBigIntegerValue());
                break;
            default:
                writeNumber(jp.getLongValue());
            }
            break;
        case VALUE_NUMBER_FLOAT:
            switch (jp.getNumberType()) {
            case BIG_DECIMAL:
                writeNumber(jp.getDecimalValue());
                break;
            case FLOAT:
                writeNumber(jp.getFloatValue());
                break;
            default:
                writeNumber(jp.getDoubleValue());
            }
            break;
        case VALUE_TRUE:
            writeBoolean(true);
            break;
        case VALUE_FALSE:
            writeBoolean(false);
            break;
        case VALUE_NULL:
            writeNull();
            break;
        case VALUE_EMBEDDED_OBJECT:
            writeObject(jp.getEmbeddedObject());
            break;
        default:
            throw new IllegalStateException();
        }
    }

    /**
     * Method for copying contents of the current event
     * <b>and following events that it encloses</b>
     * the given parser instance points to.
     *<p>
     * So what constitutes enclosing? Here is the list of
     * events that have associated enclosed events that will
     * get copied:
     *<ul>
     * <li>{@link JsonToken#START_OBJECT}:
     *   all events up to and including matching (closing)
     *   {@link JsonToken#END_OBJECT} will be copied
     *  </li>
     * <li>{@link JsonToken#START_ARRAY}
     *   all events up to and including matching (closing)
     *   {@link JsonToken#END_ARRAY} will be copied
     *  </li>
     * <li>{@link JsonToken#FIELD_NAME} the logical value (which
     *   can consist of a single scalar value; or a sequence of related
     *   events for structured types (Json Arrays, Objects)) will
     *   be copied along with the name itself. So essentially the
     *   whole <b>field entry</b> (name and value) will be copied.
     *  </li>
     *</ul>
     *<p>
     * After calling this method, parser will point to the
     * <b>last event</b> that was copied. This will either be
     * the event parser already pointed to (if there were no
     * enclosed events), or the last enclosed event copied.
     */
    @Override
    public void copyCurrentStructure(JsonParser jp) throws IOException, JsonProcessingException {
        JsonToken t = jp.getCurrentToken();

        // Let's handle field-name separately first
        if (t == JsonToken.FIELD_NAME) {
            writeFieldName(jp.getCurrentName());
            t = jp.nextToken();
            // fall-through to copy the associated value
        }

        switch (t) {
        case START_ARRAY:
            writeStartArray();
            while (jp.nextToken() != JsonToken.END_ARRAY) {
                copyCurrentStructure(jp);
            }
            writeEndArray();
            break;
        case START_OBJECT:
            writeStartObject();
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                copyCurrentStructure(jp);
            }
            writeEndObject();
            break;
        default: // others are simple:
            copyCurrentEvent(jp);
        }
    }

    /**
     * @return Context object that can give information about logical
     *   position within generated json content.
     */
    @Override
    public JsonStreamContext getOutputContext() {
        return outputContext;
    }

    /**
     * Method called to flush any buffered content to the underlying
     * target (output stream, writer), and to flush the target itself
     * as well.
     */
    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    /**
     * Method that can be called to determine whether this generator
     * is closed or not. If it is closed, no more output can be done.
     */
    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Method called to close this generator, so that no more content
     * can be written.
     *<p>
     * Whether the underlying target (stream, writer) gets closed depends
     * on whether this generator either manages the target (i.e. is the
     * only one with access to the target -- case if caller passes a
     * reference to the resource such as File, but not stream); or
     * has feature {@link Feature#AUTO_CLOSE_TARGET} enabled.
     * If either of above is true, the target is also closed. Otherwise
     * (not managing, feature not enabled), target is not closed.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (ioContext.isResourceManaged())
                outputStream.close();
            else
                outputStream.flush();
        }
    }
}
