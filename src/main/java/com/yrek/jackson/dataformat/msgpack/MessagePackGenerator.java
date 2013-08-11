package com.yrek.jackson.dataformat.msgpack;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;

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

public class MessagePackGenerator extends JsonGenerator {
    public enum Feature implements MessagePackFeature.Feature {
        PLACEHOLDER(true),
            ;

        private final boolean _defaultState;
        private Feature(boolean defaultState) {
            _defaultState = defaultState;
        }

        public boolean enabledByDefault() {
            return _defaultState;
        }
    }

    private class OutputContext extends JsonStreamContext {
        private OutputStream _out;

        OutputContext(OutputStream out) {
            _out = out;
            _type = TYPE_ROOT;
        }

        @Override
        public JsonStreamContext getParent() {
            return null;
        }

        public void setCurrentName(String currentName) throws JsonGenerationException {
            throw new JsonGenerationException("writeFieldName not in object:"+currentName);
        }

        @Override
        public String getCurrentName() {
            return null;
        }

        public OutputStream out() {
            return _out;
        }

        public void endElement() {
        }

        public OutputContext endContext() throws IOException {
            return this;
        }

        public void i8(int i8) throws IOException {
            out().write(i8);
        }

        public void i16(int i16) throws IOException {
            out().write((i16>>8)&255);
            out().write(i16&255);
        }

        public void i32(int i32) throws IOException {
            out().write((i32>>24)&255);
            out().write((i32>>16)&255);
            out().write((i32>>8)&255);
            out().write(i32&255);
        }

        public void i64(long i64) throws IOException {
            out().write((int) (i64>>56)&255);
            out().write((int) (i64>>48)&255);
            out().write((int) (i64>>40)&255);
            out().write((int) (i64>>32)&255);
            out().write((int) (i64>>24)&255);
            out().write((int) (i64>>16)&255);
            out().write((int) (i64>>8)&255);
            out().write((int) i64&255);
        }
    }

    private class ContainerOutputContext extends OutputContext {
        protected OutputContext _context;

        ContainerOutputContext(OutputContext context) {
            super(new ByteArrayOutputStream());
            _context = context;
            _index = -1;
        }

        @Override
        public JsonStreamContext getParent() {
            return _context;
        }

        @Override
        public void endElement() {
            _index++;
        }
    }

    private class ArrayOutputContext extends ContainerOutputContext {
        ArrayOutputContext(OutputContext context) {
            super(context);
            _type = TYPE_ARRAY;
        }

        @Override
        public OutputContext endContext() throws IOException {
            int count = getEntryCount();
            if (count < 16) {
                _context.i8(0x90 | count);
            } else if (count < 0x10000) {
                _context.i8(0xdc);
                _context.i16(count);
            } else {
                _context.i8(0xdd);
                _context.i32(count);
            }
            ((ByteArrayOutputStream) out()).writeTo(_context.out());
            return _context;
        }
    }

    private class MapOutputContext extends ContainerOutputContext {
        private String _currentName;

        MapOutputContext(OutputContext context) {
            super(context);
            _type = TYPE_OBJECT;
        }

        @Override
        public void setCurrentName(String currentName) throws JsonGenerationException {
            if ((getEntryCount() & 1) != 0)
                throw new JsonGenerationException("writeFieldName when field value expected:"+currentName+","+_index);
            _currentName = currentName;
        }

        @Override
        public String getCurrentName() {
            return _currentName;
        }

        @Override
        public void endElement() {
            super.endElement();
            if ((getEntryCount() & 1) == 0)
                _currentName = null;
        }

        @Override
        public OutputContext endContext() throws IOException, JsonGenerationException {
            int count = getEntryCount();
            if ((count & 1) != 0)
                throw new JsonGenerationException("Odd number of objects in map:"+count);
            count >>= 1;
            if (count < 16) {
                _context.i8(0x80 | count);
            } else if (count < 0x10000) {
                _context.i8(0xde);
                _context.i16(count);
            } else {
                _context.i8(0xdf);
                _context.i32(count);
            }
            ((ByteArrayOutputStream) out()).writeTo(_context.out());
            return _context;
        }
    }

    private IOContext _ioContext;
    private ObjectCodec _objectCodec;
    private EnumSet<MessagePackFactory.Feature> _msgPackFeatures;
    private EnumSet<Feature> _generatorFeatures;
    private OutputStream _outputStream;

    private OutputContext _outputContext;
    private Class<?> _objectContext;
    private boolean _closed;

    public MessagePackGenerator(IOContext ctxt, ObjectCodec codec, EnumSet<MessagePackFactory.Feature> msgPackFeatures, EnumSet<Feature> generatorFeatures, OutputStream out) {
        _ioContext = ctxt;
        _objectCodec = codec;
        _msgPackFeatures = msgPackFeatures;
        _generatorFeatures = generatorFeatures;
        _outputStream = out;
        
        _outputContext = new OutputContext(out);
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

    public MessagePackGenerator enable(MessagePackFactory.Feature f) {
        _msgPackFeatures.add(f);
        return this;
    }

    public MessagePackGenerator disable(MessagePackFactory.Feature f) {
        _msgPackFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(MessagePackFactory.Feature f) {
        return _msgPackFeatures.contains(f);
    }

    public MessagePackGenerator enable(Feature f) {
        _generatorFeatures.add(f);
        return this;
    }

    public MessagePackGenerator disable(Feature f) {
        _generatorFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(Feature f) {
        return _generatorFeatures.contains(f);
    }

    /**
     * Method that can be called to set or reset the object to
     * use for writing Java objects as JsonContent
     * (using method {@link #writeObject}).
     *
     * @return Generator itself (this), to allow chaining
     */
    @Override
    public JsonGenerator setCodec(ObjectCodec oc) {
        _objectCodec = oc;
        return this;
    }

    /**
     * Method for accessing the object used for writing Java
     * object as Json content
     * (using method {@link #writeObject}).
     */
    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
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
        _outputContext = new ArrayOutputContext(_outputContext);
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
        _outputContext = _outputContext.endContext();
        _outputContext.endElement();
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
        _outputContext = new MapOutputContext(_outputContext);
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
        _outputContext = _outputContext.endContext();
        _outputContext.endElement();
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
        _outputContext.setCurrentName(name);
        //... look at _objectContext for annotation for numeric field identifier
        writeString(name);
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
        _outputContext.setCurrentName(name.getValue());
        //... look at _objectContext for annotation for numeric field identifier
        writeString(name);
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
        byte[] bytes = text.getBytes("UTF-8");
        writeUTF8String(bytes, 0, bytes.length);
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
        byte[] bytes = text.asUnquotedUTF8();
        writeUTF8String(bytes, 0, bytes.length);
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
        if (len < 32) {
            _outputContext.i8(0xa0 | len);
        } else if (len < 0x10000) {
            _outputContext.i8(0xda);
            _outputContext.i16(len);
        } else {
            _outputContext.i8(0xdb);
            _outputContext.i32(len);
        }
        _outputContext.out().write(data, offset, len);
        _outputContext.endElement();
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
        if (dataLength < 0)
            throw new UnsupportedOperationException();
        byte[] buffer;
        if (dataLength < 32) {
            _outputContext.i8(0xa0 | dataLength);
            buffer = new byte[dataLength];
        } else if (dataLength < 0x10000) {
            _outputContext.i8(0xda);
            _outputContext.i16(dataLength);
            buffer = new byte[256];
        } else {
            _outputContext.i8(0xdb);
            _outputContext.i32(dataLength);
            buffer = new byte[1024];
        }
        int total;
        for (total = 0; total < dataLength; ) {
            int count = data.read(buffer);
            _outputContext.out().write(buffer, 0, count);
            total += count;
        }
        _outputContext.endElement();
        return total;
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
        writeNumber((long) v);
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
        if (v >= -32 && v < 128) {
            _outputContext.i8((int) v);
        } else if (v > 0) {
            if (v < 256) {
                _outputContext.i8(0xcc);
                _outputContext.i8((int) v);
            } else if (v < 0x10000) {
                _outputContext.i8(0xcd);
                _outputContext.i16((int) v);
            } else if (v < 0x100000000L) {
                _outputContext.i8(0xce);
                _outputContext.i32((int) v);
            } else {
                _outputContext.i8(0xcf);
                _outputContext.i64(v);
            }
        } else {
            if (v >= -128) {
                _outputContext.i8(0xd0);
                _outputContext.i8((int) v);
            } else if (v >= -0x8000) {
                _outputContext.i8(0xd1);
                _outputContext.i16((int) v);
            } else if (v >= -0x80000000) {
                _outputContext.i8(0xd2);
                _outputContext.i32((int) v);
            } else {
                _outputContext.i8(0xd3);
                _outputContext.i64(v);
            }
        }
        _outputContext.endElement();
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
        if (v.bitLength() < 64) {
            writeNumber(v.longValue());
        } else if (isEnabled(MessagePackFactory.Feature.FAIL_ON_BIG_NUMBERS)) {
            throw new JsonGenerationException("Number too big:"+v);
        } else {
            byte[] bytes = v.toByteArray();
            writeBinary(null, bytes, 0, bytes.length);
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
    public void writeNumber(double d) throws IOException, JsonGenerationException {
        _outputContext.i8(0xcb);
        _outputContext.i64(Double.doubleToLongBits(d));
        _outputContext.endElement();
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
        _outputContext.i8(0xca);
        _outputContext.i32(Float.floatToIntBits(f));
        _outputContext.endElement();
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
        try {
            writeNumber(dec.longValueExact());
            return;
        } catch (ArithmeticException e) {
        }
        if (isEnabled(MessagePackFactory.Feature.FAIL_ON_BIG_NUMBERS))
            throw new JsonGenerationException("Number too big:"+dec);
        writeString(dec.toString());
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
        _outputContext.i8(state ? 0xc3 : 0xc2);
        _outputContext.endElement();
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
        _outputContext.i8(0xc0);
        _outputContext.endElement();
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
            Class<?> saveObjectContext = _objectContext;
            _objectContext = pojo.getClass();
            _objectCodec.writeValue(this, pojo);
            _outputContext.endElement();
            _objectContext = saveObjectContext;
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
        return _outputContext;
    }

    /**
     * Method called to flush any buffered content to the underlying
     * target (output stream, writer), and to flush the target itself
     * as well.
     */
    @Override
    public void flush() throws IOException {
        _outputStream.flush();
    }

    /**
     * Method that can be called to determine whether this generator
     * is closed or not. If it is closed, no more output can be done.
     */
    @Override
    public boolean isClosed() {
        return _closed;
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
        if (!_closed) {
            _closed = true;
            if (_ioContext.isResourceManaged())
                _outputStream.close();
            else
                _outputStream.flush();
        }
    }
}
