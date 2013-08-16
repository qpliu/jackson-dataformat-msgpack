package com.yrek.jackson.dataformat.protobuf;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.Iterator;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;

import com.yrek.jackson.dataformat.msgpack.MessagePackVersion;

public class ProtobufParser extends JsonParser {
    private class LimitedInputStream extends FilterInputStream {
        private final int limit;
        private int count;
        private int markedCount;

        LimitedInputStream(InputStream in, int limit) {
            super(in);
            this.limit = limit;
            this.count = 0;
        }

        @Override
        public int read() throws IOException {
            if (count >= limit)
                return -1;
            count++;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (count >= limit)
                return 0;
            int n = super.read(b, off, Math.min(len, limit - count));
            count += n;
            return n;
        }

        @Override
        public void mark(int readlimit) {
            super.mark(readlimit);
            markedCount = count;
        }

        @Override
        public void reset() throws IOException {
            super.reset();
            count = markedCount;
        }

        @Override
        public long skip(long n) throws IOException {
            if (count >= limit)
                return 0;
            long l = super.skip(Math.max(n, (long) (limit - count)));
            count += (int) l;
            return l;
        }

        public int getRemaining() {
            return limit - count;
        }

        public void skipRemaining() throws IOException {
            if (limit > count) {
                skip(limit - count);
                count = limit;
            }
        }
    }

    private class InputContext extends JsonStreamContext {
        protected final InputStream in;
        protected final InputContext parent;
        protected final MessageDescription objectContext;
        protected final MessageField fieldContext;
        protected String currentName = null;
        protected JsonToken endToken = null;
        protected InputContext nextInputContext;

        protected JsonToken fieldValueToken = null;
        protected long integralValue;
        protected float floatValue;
        protected double doubleValue;
        protected boolean floatValueIsDouble;
        protected String stringValue;
        protected byte[] bytesValue;
        protected InputContext fieldInputContext;

        InputContext(InputStream in, MessageDescription objectContext) {
            this(new BufferedInputStream(in, 10), null, objectContext, null, TYPE_ROOT);
        }

        InputContext(InputStream in, InputContext parent, MessageDescription objectContext, MessageField fieldContext, int type) {
            this.in = in;
            this.parent = parent;
            this.objectContext = objectContext;
            this.fieldContext = fieldContext;

            this.nextInputContext = this;
            this._type = type;
            this._index = -1;
        }

        @Override
        public JsonStreamContext getParent() {
            return parent;
        }

        @Override
        public String getCurrentName() {
            return currentName;
        }

        public long getTokenLocation() {
            return -1;
        }

        public long getCurrentLocation() {
            return -1;
        }

        public void setCurrentName(String currentName) {
            this.currentName = currentName;
        }

        public InputStream in() {
            return in;
        }

        public InputContext nextInputContext() {
            return nextInputContext;
        }

        public JsonToken nextToken() throws IOException {
            nextInputContext = this;
            if (fieldValueToken != null) {
                JsonToken t = fieldValueToken;
                fieldValueToken = null;
                nextInputContext = fieldInputContext;
                return t;
            }
            stringValue = null;
            bytesValue = null;
            if (endToken == null) {
                if (inArray())
                    endToken = JsonToken.END_ARRAY;
                else
                    endToken = JsonToken.END_OBJECT;
                if (inRoot())
                    return atEOF() ? null : JsonToken.START_OBJECT;
            }
            for (;;) {
                if (atEOF()) {
                    nextInputContext = parent;
                    return endToken;
                }
                JsonToken elementToken = readElement();
                if (elementToken != null) {
                    _index++;
                    return elementToken;
                }
            }
        }

        protected JsonToken readElement() throws IOException {
            long key = peekVarint();
            WireType wireType = WireType.getWireType((int) key);
            if (wireType == null)
                throw _constructError("Invalid wire type:"+(key&7));
            MessageField messageField = objectContext.getMessageField((int) key>>>3);
            if (messageField == null) {
                varint();
                skipValue(wireType);
                return null;
            }
            currentName = messageField.getName();
            if (wireType == WireType.LengthDelimited && messageField.isPacked()) {
                varint();
                fieldValueToken = JsonToken.START_ARRAY;
                fieldInputContext = new PackedInputContext(this, (int) varint(), messageField);
                return JsonToken.FIELD_NAME;
            }
            if (!inRepeating() && messageField.isRepeated()) {
                fieldValueToken = JsonToken.START_ARRAY;
                fieldInputContext = new RepeatedInputContext(this, objectContext, messageField);
                return JsonToken.FIELD_NAME;
            }
            if (wireType == WireType.LengthDelimited && messageField.isMessageType()) {
                varint();
                MessageDescription messageDescription = schema.getMessageDescription(messageField);
                if (messageDescription == null) {
                    skipValue(wireType);
                    return null;
                }
                fieldValueToken = JsonToken.START_OBJECT;
                fieldInputContext = new LengthDelimitedInputContext(this, (int) varint(), messageDescription, null);
                return JsonToken.FIELD_NAME;
            }
            varint();
            return readElement(wireType, messageField);
        }

        @SuppressWarnings("fallthrough")
        protected JsonToken readElement(WireType wireType, MessageField messageField) throws IOException {
            fieldInputContext = this;
            switch (wireType) {
            case Varint:
                integralValue = varint();
                fieldValueToken = JsonToken.VALUE_NUMBER_INT;
                if (messageField.isEnumType()) {
                    EnumDescription enumDescription = schema.getEnumDescription(messageField);
                    if (enumDescription != null && enumDescription.getName((int) integralValue) != null) {
                        fieldValueToken = JsonToken.VALUE_STRING;
                        stringValue = enumDescription.getName((int) integralValue);
                    }
                } else if (messageField.isBoolean()) {
                    fieldValueToken = integralValue == 0 ? JsonToken.VALUE_FALSE : JsonToken.VALUE_TRUE;
                }
                switch (messageField.getProtobufType()) {
                case SINT32: case SINT64:
                    integralValue = unzigzag(integralValue);
                    break;
                case BOOL:
                    fieldValueToken = integralValue == 0 ? JsonToken.VALUE_FALSE : JsonToken.VALUE_TRUE;
                    break;
                }
                floatValue = (float) integralValue;
                doubleValue = (double) integralValue;
                floatValueIsDouble = false;
                break;
            case Fixed64:
                integralValue = fixed64();
                floatValue = (float) integralValue;
                doubleValue = (double) integralValue;
                floatValueIsDouble = false;
                fieldValueToken = JsonToken.VALUE_NUMBER_INT;
                switch (messageField.getProtobufType()) {
                case SFIXED64:
                    integralValue = unzigzag(integralValue);
                    floatValue = (float) integralValue;
                    doubleValue = (double) integralValue;
                    break;
                case DOUBLE:
                    floatValueIsDouble = true;
                    /*FALLTHROUGH*/
                case FLOAT:
                    fieldValueToken = JsonToken.VALUE_NUMBER_FLOAT;
                    doubleValue = Double.longBitsToDouble(integralValue);
                    floatValue = (float) doubleValue;
                    integralValue = (long) doubleValue;
                }
                break;
            case LengthDelimited:
                stringValue = null;
                bytesValue = new byte[(int) varint()];
                in().read(bytesValue);
                fieldValueToken = JsonToken.VALUE_STRING;
                break;
            case StartGroup: case EndGroup:
                throw _constructError("Unsupported wire type:"+wireType);
            case Fixed32:
                integralValue = fixed32();
                floatValue = (float) integralValue;
                doubleValue = (double) integralValue;
                floatValueIsDouble = false;
                fieldValueToken = JsonToken.VALUE_NUMBER_INT;
                switch (messageField.getProtobufType()) {
                case SFIXED32:
                    integralValue = unzigzag(integralValue);
                    floatValue = (float) integralValue;
                    doubleValue = (double) integralValue;
                    break;
                case DOUBLE: case FLOAT:
                    fieldValueToken = JsonToken.VALUE_NUMBER_FLOAT;
                    floatValue = Float.intBitsToFloat((int) integralValue);
                    doubleValue = (double) floatValue;
                    integralValue = (long) floatValue;
                    break;
                }
                break;
            }
            return JsonToken.FIELD_NAME;
        }

        protected boolean inRepeating() {
            return false;
        }

        protected void skipValue(WireType wireType) throws IOException {
            switch (wireType) {
            case Varint:
                varint();
                break;
            case Fixed64:
                in().skip(8);
                break;
            case LengthDelimited:
                in().skip(varint());
                break;
            case StartGroup: case EndGroup:
                throw _constructError("Unsupported wire type:"+wireType);
            case Fixed32:
                in().skip(4);
                break;
            }
        }

        public boolean atEOF() throws IOException {
            in.mark(1);
            int i = in.read();
            in.reset();
            return i < 0;
        }

        public long peekVarint() throws IOException {
            in.mark(10); // up to 70 bits
            long n = varint();
            in.reset();
            return n;
        }

        public long varint() throws IOException {
            long n = 0L;
            for (int i = 0; i < 10; i++) {
                int b = in.read();
                if (b < 0)
                    throw _constructError("Unexpected EOF");
                n |= (long) (b&127) << (long) (i*7);
                if ((b&128) == 0)
                    return n;
            }
            throw _constructError("varint overflow");
        }

        public long fixed64() throws IOException {
            long n = 0L;
            for (int i = 0; i < 8; i++) {
                int b = in.read();
                if (b < 0)
                    throw _constructError("Unexpected EOF");
                n |= (long) b << ((long) i*8);
            }
            return n;
        }

        public int fixed32() throws IOException {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int b = in.read();
                if (b < 0)
                    throw _constructError("Unexpected EOF");
                n |= b << (i*8);
            }
            return n;
        }

        public long unzigzag(long n) {
            return (n&1) == 0 ? n>>>1 : ~(n>>>1);
        }
    }

    private class RepeatedInputContext extends InputContext {
        RepeatedInputContext(InputContext parent, MessageDescription objectContext, MessageField fieldContext) {
            super(parent.in(), parent, objectContext, fieldContext, TYPE_ARRAY);
        }

        @Override
        protected JsonToken readElement() throws IOException {
            long key = peekVarint();
            if (fieldContext.getTag() != (int) (key>>>3)) {
                nextInputContext = parent;
                return JsonToken.END_ARRAY;
            }
            super.readElement();
            JsonToken t = fieldValueToken;
            fieldValueToken = null;
            nextInputContext = fieldInputContext;
            return t;
        }

        @Override
        protected boolean inRepeating() {
            return true;
        }
    }

    private class LengthDelimitedInputContext extends InputContext {
        LengthDelimitedInputContext(InputContext parent, int length, MessageDescription objectContext, MessageField fieldContext) {
            super(new LimitedInputStream(parent.in(), length), parent, objectContext, fieldContext, objectContext != null ? TYPE_OBJECT : TYPE_ARRAY);
        }
    }

    private class PackedInputContext extends LengthDelimitedInputContext {
        private final WireType wireType;

        PackedInputContext(InputContext parent, int limit, MessageField fieldContext) {
            super(parent, limit, null, fieldContext);
            if (!fieldContext.isPacked())
                throw new IllegalArgumentException();
            this.wireType = fieldContext.packedWireType();
        }

        @Override
        protected JsonToken readElement() throws IOException {
            readElement(wireType, fieldContext);
            JsonToken t = fieldValueToken;
            fieldValueToken = null;
            assert fieldInputContext == this;
            return t;
        }
    }

    private IOContext ioContext;
    private ObjectCodec objectCodec;
    private InputStream inputStream;
    private ProtobufSchema schema;

    private boolean closed;
    private InputContext inputContext;
    private JsonToken currentToken;
    private JsonToken lastClearedToken;

    public ProtobufParser(IOContext ioContext, ObjectCodec objectCodec, InputStream inputStream, MessageDescription objectContext, ProtobufSchema schema) {
        this.ioContext = ioContext;
        this.objectCodec = objectCodec;
        this.inputStream = inputStream;
        this.schema = schema;

        this.inputContext = new InputContext(inputStream, objectContext);
    }

    public ProtobufParser(IOContext ioContext, ObjectCodec objectCodec, InputStream inputStream) {
        this.ioContext = ioContext;
        this.objectCodec = objectCodec;
        this.inputStream = inputStream;
    }

    public void initContext(MessageDescription objectContext, ProtobufSchema schema) {
        this.schema = schema;
        this.inputContext = new InputContext(inputStream, objectContext);
    }

    /**
     * Accessor for {@link ObjectCodec} associated with this
     * parser, if any. Codec is used by {@link #readValueAs(Class)}
     * method (and its variants).
     *
     * @since 1.3
     */
    @Override
    public ObjectCodec getCodec() {
        return objectCodec;
    }

    /**
     * Setter that allows defining {@link ObjectCodec} associated with this
     * parser, if any. Codec is used by {@link #readValueAs(Class)}
     * method (and its variants).
     *
     * @since 1.3
     */
    @Override
    public void setCodec(ObjectCodec objectCodec) {
        this.objectCodec = objectCodec;
    }

    /**
     * Accessor for getting version of the core package, given a parser instance.
     * Left for sub-classes to implement.
     */
    @Override
    public Version version() {
        return MessagePackVersion.VERSION;
    }

    /**
     * Closes the parser so that no further iteration or data access
     * can be made; will also close the underlying input source
     * if parser either <b>owns</b> the input source, or feature
     * {@link Feature#AUTO_CLOSE_SOURCE} is enabled.
     * Whether parser owns the input source depends on factory
     * method that was used to construct instance (so check
     * {@link com.fasterxml.jackson.core.JsonFactory} for details,
     * but the general
     * idea is that if caller passes in closable resource (such
     * as {@link InputStream} or {@link Reader}) parser does NOT
     * own the source; but if it passes a reference (such as
     * {@link java.io.File} or {@link java.net.URL} and creates
     * stream or reader it does own them.
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (ioContext.isResourceManaged())
                inputStream.close();
        }
    }

    /**
     * Main iteration method, which will advance stream enough
     * to determine type of the next token, if any. If none
     * remaining (stream has no content other than possible
     * white space before ending), null will be returned.
     *
     * @return Next token from the stream, if any found, or null
     *   to indicate end-of-input
     */
    @Override
    public JsonToken nextToken() throws IOException, JsonParseException {
        if (inputContext == null)
            return null;
        currentToken = inputContext.nextToken();
        inputContext = inputContext.nextInputContext();
        return currentToken;
    }

    /**
     * Iteration method that will advance stream enough
     * to determine type of the next token that is a value type
     * (including JSON Array and Object start/end markers).
     * Or put another way, nextToken() will be called once,
     * and if {@link JsonToken#FIELD_NAME} is returned, another
     * time to get the value for the field.
     * Method is most useful for iterating over value entries
     * of JSON objects; field name will still be available
     * by calling {@link #getCurrentName} when parser points to
     * the value.
     *
     * @return Next non-field-name token from the stream, if any found,
     *   or null to indicate end-of-input (or, for non-blocking
     *   parsers, {@link JsonToken#NOT_AVAILABLE} if no tokens were
     *   available yet)
     */
    @Override
    public JsonToken nextValue() throws IOException, JsonParseException {
        /* Implementation should be as trivial as follows; only
         * needs to change if we are to skip other tokens (for
         * example, if comments were exposed as tokens)
         */
        JsonToken t = nextToken();
        if (t == JsonToken.FIELD_NAME) {
            t = nextToken();
        }
        return t;
    }

    /**
     * Method that will skip all child tokens of an array or
     * object token that the parser currently points to,
     * iff stream points to 
     * {@link JsonToken#START_OBJECT} or {@link JsonToken#START_ARRAY}.
     * If not, it will do nothing.
     * After skipping, stream will point to <b>matching</b>
     * {@link JsonToken#END_OBJECT} or {@link JsonToken#END_ARRAY}
     * (possibly skipping nested pairs of START/END OBJECT/ARRAY tokens
     * as well as value tokens).
     * The idea is that after calling this method, application
     * will call {@link #nextToken} to point to the next
     * available token, if any.
     */
    @Override
    public JsonParser skipChildren() throws IOException, JsonParseException {
        if (inputContext.inRoot() || inputContext.getEntryCount() > 0)
            return this;
        int open = 1;

        /* Since proper matching of start/end markers is handled
         * by nextToken(), we'll just count nesting levels here
         */
        while (true) {
            JsonToken t = nextToken();
            if (t == null) {
                throw _constructError("Unexpected EOF");
            }
            switch (t) {
            case START_OBJECT:
            case START_ARRAY:
                ++open;
                break;
            case END_OBJECT:
            case END_ARRAY:
                if (--open == 0) {
                    return this;
                }
                break;
            default:
            }
        }
    }

    /**
     * Method that can be called to determine whether this parser
     * is closed or not. If it is closed, no new tokens can be
     * retrieved by calling {@link #nextToken} (and the underlying
     * stream may be closed). Closing may be due to an explicit
     * call to {@link #close} or because parser has encountered
     * end of input.
     */
    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Accessor to find which token parser currently points to, if any;
     * null will be returned if none.
     * If return value is non-null, data associated with the token
     * is available via other accessor methods.
     *
     * @return Type of the token this parser currently points to,
     *   if any: null before any tokens have been read, and
     *   after end-of-input has been encountered, as well as
     *   if the current token has been explicitly cleared.
     */
    @Override
    public JsonToken getCurrentToken() {
        return currentToken;
    }

    /**
     * Method for checking whether parser currently points to
     * a token (and data for that token is available).
     * Equivalent to check for <code>parser.getCurrentToken() != null</code>.
     *
     * @return True if the parser just returned a valid
     *   token via {@link #nextToken}; false otherwise (parser
     *   was just constructed, encountered end-of-input
     *   and returned null from {@link #nextToken}, or the token
     *   has been consumed)
     */
    @Override
    public boolean hasCurrentToken() {
        return currentToken != null;
    }

    /**
     * Method that can be called to get the name associated with
     * the current token: for {@link JsonToken#FIELD_NAME}s it will
     * be the same as what {@link #getText} returns;
     * for field values it will be preceding field name;
     * and for others (array values, root-level values) null.
     */
    @Override
    public String getCurrentName() throws IOException, JsonParseException {
        return inputContext.getCurrentName();
    }

    /**
     * Method that can be used to access current parsing context reader
     * is in. There are 3 different types: root, array and object contexts,
     * with slightly different available information. Contexts are
     * hierarchically nested, and can be used for example for figuring
     * out part of the input document that correspond to specific
     * array or object (for highlighting purposes, or error reporting).
     * Contexts can also be used for simple xpath-like matching of
     * input, if so desired.
     */
    @Override
    public JsonStreamContext getParsingContext() {
        return inputContext;
    }

    /**
     * Method that return the <b>starting</b> location of the current
     * token; that is, position of the first character from input
     * that starts the current token.
     */
    @Override
    public JsonLocation getTokenLocation() {
        return new JsonLocation(ioContext.getSourceReference(), inputContext.getTokenLocation(), -1, -1, -1);
    }

    /**
     * Method that returns location of the last processed character;
     * usually for error reporting purposes.
     */
    @Override
    public JsonLocation getCurrentLocation() {
        return new JsonLocation(ioContext.getSourceReference(), inputContext.getCurrentLocation(), -1, 0, 0);
    }

    /**
     * Method called to "consume" the current token by effectively
     * removing it so that {@link #hasCurrentToken} returns false, and
     * {@link #getCurrentToken} null).
     * Cleared token value can still be accessed by calling
     * {@link #getLastClearedToken} (if absolutely needed), but
     * usually isn't.
     *<p>
     * Method was added to be used by the optional data binder, since
     * it has to be able to consume last token used for binding (so that
     * it will not be used again).
     */
    @Override
    public void clearCurrentToken() {
        if (currentToken != null) {
            lastClearedToken = currentToken;
            currentToken = null;
        }
    }

    /**
     * Method that can be called to get the last token that was
     * cleared using {@link #clearCurrentToken}. This is not necessarily
     * the latest token read.
     * Will return null if no tokens have been cleared,
     * or if parser has been closed.
     */
    @Override
    public JsonToken getLastClearedToken() {
        return lastClearedToken;
    }
    
    /**
     * Method that can be used to change what is considered to be
     * the current (field) name.
     * May be needed to support non-JSON data formats or unusual binding
     * conventions; not needed for typical processing.
     *<p>
     * Note that use of this method should only be done as sort of last
     * resort, as it is a work-around for regular operation.
     * 
     * @param name Name to use as the current name; may be null.
     * 
     * @since 2.0
     */
    @Override
    public void overrideCurrentName(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Method for accessing textual representation of the current token;
     * if no current token (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any token type.
     */
    @Override
    public String getText() throws IOException, JsonParseException {
        if (inputContext.stringValue != null)
            return inputContext.stringValue;
        if (inputContext.bytesValue != null)
            return new String(inputContext.bytesValue, "UTF-8");
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_FLOAT)
            return String.valueOf(inputContext.doubleValue);
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_INT)
            return String.valueOf(inputContext.integralValue);
        if (inputContext.fieldValueToken != null)
            return inputContext.fieldValueToken.toString();
        return null;
    }

    /**
     * Method similar to {@link #getText}, but that will return
     * underlying (unmodifiable) character array that contains
     * textual value, instead of constructing a String object
     * to contain this information.
     * Note, however, that:
     *<ul>
     * <li>Textual contents are not guaranteed to start at
     *   index 0 (rather, call {@link #getTextOffset}) to
     *   know the actual offset
     *  </li>
     * <li>Length of textual contents may be less than the
     *  length of returned buffer: call {@link #getTextLength}
     *  for actual length of returned content.
     *  </li>
     * </ul>
     *<p>
     * Note that caller <b>MUST NOT</b> modify the returned
     * character array in any way -- doing so may corrupt
     * current parser state and render parser instance useless.
     *<p>
     * The only reason to call this method (over {@link #getText})
     * is to avoid construction of a String object (which
     * will make a copy of contents).
     */
    @Override
    public char[] getTextCharacters() throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    /**
     * Accessor used with {@link #getTextCharacters}, to know length
     * of String stored in returned buffer.
     *
     * @return Number of characters within buffer returned
     *   by {@link #getTextCharacters} that are part of
     *   textual content of the current token.
     */
    @Override
    public int getTextLength() throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    /**
     * Accessor used with {@link #getTextCharacters}, to know offset
     * of the first text content character within buffer.
     *
     * @return Offset of the first character within buffer returned
     *   by {@link #getTextCharacters} that is part of
     *   textual content of the current token.
     */
    @Override
    public int getTextOffset() throws IOException, JsonParseException {
        throw new UnsupportedOperationException();
    }

    /**
     * Method that can be used to determine whether calling of
     * {@link #getTextCharacters} would be the most efficient
     * way to access textual content for the event parser currently
     * points to.
     *<p> 
     * Default implementation simply returns false since only actual
     * implementation class has knowledge of its internal buffering
     * state.
     * Implementations are strongly encouraged to properly override
     * this method, to allow efficient copying of content by other
     * code.
     * 
     * @return True if parser currently has character array that can
     *   be efficiently returned via {@link #getTextCharacters}; false
     *   means that it may or may not exist
     */
    @Override
    public boolean hasTextCharacters() {
        return false;
    }

    /**
     * Generic number value accessor method that will work for
     * all kinds of numeric values. It will return the optimal
     * (simplest/smallest possible) wrapper object that can
     * express the numeric value just parsed.
     */
    @Override
    public Number getNumberValue() throws IOException, JsonParseException {
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_FLOAT)
            return inputContext.floatValueIsDouble ? Double.valueOf(inputContext.doubleValue) : Float.valueOf(inputContext.floatValue);
        if (inputContext.integralValue > Integer.MAX_VALUE || inputContext.integralValue < Integer.MIN_VALUE)
            return Long.valueOf(inputContext.integralValue);
        return Integer.valueOf((int) inputContext.integralValue);
    }

    /**
     * If current token is of type 
     * {@link JsonToken#VALUE_NUMBER_INT} or
     * {@link JsonToken#VALUE_NUMBER_FLOAT}, returns
     * one of {@link NumberType} constants; otherwise returns null.
     */
    @Override
    public JsonParser.NumberType getNumberType() throws IOException, JsonParseException {
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_FLOAT)
            return inputContext.floatValueIsDouble ? JsonParser.NumberType.DOUBLE : JsonParser.NumberType.FLOAT;
        if (inputContext.integralValue > Integer.MAX_VALUE || inputContext.integralValue < Integer.MIN_VALUE)
            return JsonParser.NumberType.LONG;
        return JsonParser.NumberType.INT;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_INT} and
     * it can be expressed as a value of Java int primitive type.
     * It can also be called for {@link JsonToken#VALUE_NUMBER_FLOAT};
     * if so, it is equivalent to calling {@link #getDoubleValue}
     * and then casting; except for possible overflow/underflow
     * exception.
     *<p>
     * Note: if the resulting integer value falls outside range of
     * Java int, a {@link JsonParseException}
     * may be thrown to indicate numeric overflow/underflow.
     */
    @Override
    public int getIntValue() throws IOException, JsonParseException {
        return (int) inputContext.integralValue;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_INT} and
     * it can be expressed as a Java long primitive type.
     * It can also be called for {@link JsonToken#VALUE_NUMBER_FLOAT};
     * if so, it is equivalent to calling {@link #getDoubleValue}
     * and then casting to int; except for possible overflow/underflow
     * exception.
     *<p>
     * Note: if the token is an integer, but its value falls
     * outside of range of Java long, a {@link JsonParseException}
     * may be thrown to indicate numeric overflow/underflow.
     */
    @Override
    public long getLongValue() throws IOException, JsonParseException {
        return inputContext.integralValue;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_INT} and
     * it can not be used as a Java long primitive type due to its
     * magnitude.
     * It can also be called for {@link JsonToken#VALUE_NUMBER_FLOAT};
     * if so, it is equivalent to calling {@link #getDecimalValue}
     * and then constructing a {@link BigInteger} from that value.
     */
    @Override
    public BigInteger getBigIntegerValue() throws IOException, JsonParseException {
        return BigInteger.valueOf(inputContext.integralValue);
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_FLOAT} and
     * it can be expressed as a Java float primitive type.
     * It can also be called for {@link JsonToken#VALUE_NUMBER_INT};
     * if so, it is equivalent to calling {@link #getLongValue}
     * and then casting; except for possible overflow/underflow
     * exception.
     *<p>
     * Note: if the value falls
     * outside of range of Java float, a {@link JsonParseException}
     * will be thrown to indicate numeric overflow/underflow.
     */
    @Override
    public float getFloatValue() throws IOException, JsonParseException {
        return inputContext.floatValue;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_FLOAT} and
     * it can be expressed as a Java double primitive type.
     * It can also be called for {@link JsonToken#VALUE_NUMBER_INT};
     * if so, it is equivalent to calling {@link #getLongValue}
     * and then casting; except for possible overflow/underflow
     * exception.
     *<p>
     * Note: if the value falls
     * outside of range of Java double, a {@link JsonParseException}
     * will be thrown to indicate numeric overflow/underflow.
     */
    @Override
    public double getDoubleValue() throws IOException, JsonParseException {
        return inputContext.doubleValue;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_FLOAT} or
     * {@link JsonToken#VALUE_NUMBER_INT}. No under/overflow exceptions
     * are ever thrown.
     */
    @Override
    public BigDecimal getDecimalValue() throws IOException, JsonParseException {
        return BigDecimal.valueOf(inputContext.doubleValue);
    }

    /**
     * Accessor that can be called if (and only if) the current token
     * is {@link JsonToken#VALUE_EMBEDDED_OBJECT}. For other token types,
     * null is returned.
     *<p>
     * Note: only some specialized parser implementations support
     * embedding of objects (usually ones that are facades on top
     * of non-streaming sources, such as object trees).
     */
    @Override
    public Object getEmbeddedObject() throws IOException, JsonParseException {
        throw new IllegalStateException();
    }

    /**
     * Method that can be used to read (and consume -- results
     * may not be accessible using other methods after the call)
     * base64-encoded binary data
     * included in the current textual JSON value.
     * It works similar to getting String value via {@link #getText}
     * and decoding result (except for decoding part),
     * but should be significantly more performant.
     *<p>
     * Note that non-decoded textual contents of the current token
     * are not guaranteed to be accessible after this method
     * is called. Current implementation, for example, clears up
     * textual content during decoding.
     * Decoded binary content, however, will be retained until
     * parser is advanced to the next event.
     *
     * @param b64variant Expected variant of base64 encoded
     *   content (see {@link Base64Variants} for definitions
     *   of "standard" variants).
     *
     * @return Decoded binary data
     */
    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException, JsonParseException {
        return inputContext.bytesValue;
    }

    /**
     * Method that will try to convert value of current token to a
     * {@link java.lang.String}.
     * JSON Strings map naturally; scalar values get converted to
     * their textual representation.
     * If representation can not be converted to a String value (including structured types
     * like Objects and Arrays and null token), specified default value
     * will be returned; no exceptions are thrown.
     * 
     * @since 2.1
     */
    @Override
    public String getValueAsString(String defaultValue) throws IOException, JsonParseException {
        if (inputContext.stringValue != null)
            return inputContext.stringValue;
        if (inputContext.bytesValue != null) {
            inputContext.stringValue = new String(inputContext.bytesValue, "UTF-8");
            return inputContext.stringValue;
        }
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_FLOAT)
            return String.valueOf(inputContext.doubleValue);
        if (inputContext.fieldValueToken == JsonToken.VALUE_NUMBER_INT)
            return String.valueOf(inputContext.integralValue);
        return defaultValue;
    }
}
