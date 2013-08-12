package com.yrek.jackson.dataformat.msgpack;

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

public class MessagePackParser extends JsonParser {
    public enum Feature implements MessagePackFeature.Feature {
        PLACEHOLDER(true),
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

    private enum ValueType {
        INT,
        NIL,
        BOOLEAN,
        FLOAT,
        DOUBLE,
        BYTES,
    }

    private class InputContext extends JsonStreamContext {
        InputContext _nextInputContext;
        ValueType _valueType;
        long _intValue;
        boolean _intValueSigned;
        boolean _boolValue;
        float _floatValue;
        double _doubleValue;
        byte[] _bytesValue;
        String _stringValue;

        InputContext() {
            _type = TYPE_ROOT;
            _index = -1;
        }

        @Override
        public JsonStreamContext getParent() {
            return null;
        }

        @Override
        public String getCurrentName() {
            return null;
        }

        JsonToken nextToken() throws IOException, JsonParseException {
            _index++;
            _nextInputContext = this;
            _valueType = null;
            _bytesValue = null;
            _stringValue = null;
            _currentTokenLocation = _currentInputCount;
            int tokenByte = _inputStream.read();
            if (tokenByte < 0)
                return null;
            _currentInputCount++;
            if (tokenByte < 0x80 || tokenByte >= 0xe0) {
                _valueType = ValueType.INT;
                _intValue = tokenByte >= 0xe0 ? tokenByte - 256 : tokenByte;
                return intValue();
            }
            if ((tokenByte & 0xf0) == 0x80) {
                _nextInputContext = new MapInputContext(tokenByte & 0xf,this);
                return JsonToken.START_OBJECT;
            }
            if ((tokenByte & 0xf0) == 0x90) {
                _nextInputContext = new ArrayInputContext(tokenByte & 0xf,this);
                return JsonToken.START_ARRAY;
            }
            if ((tokenByte & 0xe0) == 0xa0)
                return bytesValue(tokenByte & 0x1f);
            switch (tokenByte) {
            case 0xc0:
                _valueType = ValueType.NIL;
                return JsonToken.VALUE_NULL;
            case 0xc2:
                _valueType = ValueType.BOOLEAN;
                _boolValue = false;
                return JsonToken.VALUE_FALSE;
            case 0xc3:
                _valueType = ValueType.BOOLEAN;
                _boolValue = true;
                return JsonToken.VALUE_TRUE;
            case 0xca:
                _valueType = ValueType.FLOAT;
                _floatValue = Float.intBitsToFloat((int) read32());
                return JsonToken.VALUE_NUMBER_FLOAT;
            case 0xcb:
                _valueType = ValueType.DOUBLE;
                _doubleValue = Double.longBitsToDouble(read64());
                return JsonToken.VALUE_NUMBER_FLOAT;
            case 0xcc:
                _valueType = ValueType.INT;
                _intValue = read8();
                _intValueSigned = false;
                return intValue();
            case 0xcd:
                _valueType = ValueType.INT;
                _intValue = read16();
                _intValueSigned = false;
                return intValue();
            case 0xce:
                _valueType = ValueType.INT;
                _intValue = read32();
                _intValueSigned = false;
                return intValue();
            case 0xcf:
                _valueType = ValueType.INT;
                _intValue = read64();
                _intValueSigned = false;
                return intValue();
            case 0xd0:
                _valueType = ValueType.INT;
                _intValue = read8();
                _intValueSigned = true;
                return intValue();
            case 0xd1:
                _valueType = ValueType.INT;
                _intValue = read16();
                _intValueSigned = true;
                if (_intValue > Short.MAX_VALUE)
                    _intValue = Short.MIN_VALUE + _intValue - Short.MAX_VALUE - 1;
                return intValue();
            case 0xd2:
                _valueType = ValueType.INT;
                _intValue = read32();
                _intValueSigned = true;
                if (_intValue > Integer.MAX_VALUE)
                    _intValue = Integer.MIN_VALUE + _intValue - Integer.MAX_VALUE - 1;
                return intValue();
            case 0xd3:
                _valueType = ValueType.INT;
                _intValue = read64();
                _intValueSigned = true;
                return intValue();
            case 0xda:
                return bytesValue(read16());
            case 0xdb:
                return bytesValue(read32());
            case 0xdc:
                _nextInputContext = new ArrayInputContext(read16(),this);
                return JsonToken.START_ARRAY;
            case 0xdd: {
                int n = read32();
                if (n < 0)
                    throw _constructError("Array too big:"+n);
                _nextInputContext = new ArrayInputContext(n,this);
                return JsonToken.START_ARRAY;
            }
            case 0xde:
                _nextInputContext = new MapInputContext(read16(),this);
                return JsonToken.START_OBJECT;
            case 0xdf: {
                int n = read32();
                if (n < 0 || 2*n < 0)
                    throw _constructError("Map too big:"+n);
                _nextInputContext = new MapInputContext(n,this);
                return JsonToken.START_OBJECT;
            }
            }
            throw _constructError("Unrecognized byte:"+tokenByte);
        }

        private JsonToken intValue() {
            if (_objectContext != null && _introspectionResults != null && _objectContext.isEnumType()) {
                _stringValue = _introspectionResults.getEnum(_objectContext, (int) _intValue);
                if (_stringValue != null)
                    return JsonToken.VALUE_STRING;
            }
            return JsonToken.VALUE_NUMBER_INT;
        }

        private JsonToken bytesValue(int size) throws IOException, JsonParseException {
            if (size < 0)
                throw _constructError("Byte array too big:"+size);
            _valueType = ValueType.BYTES;
            _bytesValue = new byte[size];
            _currentInputCount += _inputStream.read(_bytesValue);
            return JsonToken.VALUE_STRING;
        }

        InputContext nextInputContext() {
            return _nextInputContext;
        }

        private int read8() throws IOException, JsonParseException {
            int b = _inputStream.read();
            if (b < 0)
                throw _constructError("Unexpected EOF");
            _currentInputCount++;
            return b;
        }

        private int read16() throws IOException, JsonParseException {
            return (read8()<<8) | read8();
        }

        private int read32() throws IOException, JsonParseException {
            return (read16()<<16) | read16();
        }

        private long read64() throws IOException, JsonParseException {
            return (((long) read32())<<32) | (long) read32();
        }
    }

    private class ContainerInputContext extends InputContext {
        final int _size;
        final InputContext _parent;
        final JsonToken _endToken;
        final JavaType _parentObjectContext;

        ContainerInputContext(int size, InputContext parent, JsonToken endToken) {
            _size = size;
            _parent = parent;
            _endToken = endToken;
            _parentObjectContext = _objectContext;
            if (_introspectionResults != null && _objectContext != null) {
                if (parent.inObject() && parent.getCurrentName() != null)
                    _objectContext = _introspectionResults.getType(_objectContext, parent.getCurrentName());
                else if (parent.inArray())
                    _objectContext = _objectContext.getContentType();
                else if (!parent.inRoot())
                    _objectContext = null;
            }
        }

        @Override
        JsonToken nextToken() throws IOException, JsonParseException {
            if (getEntryCount() < _size)
                return super.nextToken();
            _nextInputContext = _parent;
            _objectContext = _parentObjectContext;
            return _endToken;
        }

        @Override
        public JsonStreamContext getParent() {
            return _parent;
        }
    }

    private class ArrayInputContext extends ContainerInputContext {
        ArrayInputContext(int size, InputContext parent) {
            super(size, parent, JsonToken.END_ARRAY);
            _type = TYPE_ARRAY;
        }
    }


    private class MapInputContext extends ContainerInputContext {
        String _currentName;

        MapInputContext(int size, InputContext parent) {
            super(2*size, parent, JsonToken.END_OBJECT);
            _type = TYPE_OBJECT;
        }

        @Override
        JsonToken nextToken() throws IOException, JsonParseException {
            if (getEntryCount() >= _size)
                return super.nextToken();
            if (getEntryCount()%2 == 1)
                return super.nextToken();
            super.nextToken();
            if (_valueType == null)
                throw _constructError("Unexpected key type");
            switch (_valueType) {
            case BYTES:
                _stringValue = new String(_bytesValue,"UTF-8");
                _currentName = _stringValue;
                return JsonToken.FIELD_NAME;
            case INT:
                if (_objectContext != null && _introspectionResults != null) {
                    _stringValue = _introspectionResults.getName(_objectContext, (int) _intValue);
                    if (_stringValue != null) {
                        _currentName = _stringValue;
                        return JsonToken.FIELD_NAME;
                    }
                }
                // FALLTHROUGH
            default:
                throw _constructError("Unexpected key type");
            }
        }

        @Override
        public String getCurrentName() {
            return _currentName;
        }
    }

    private IOContext _ioContext;
    private ObjectCodec _objectCodec;
    private EnumSet<MessagePackFactory.Feature> _msgPackFeatures;
    private EnumSet<Feature> _parserFeatures;
    private InputStream _inputStream;

    private boolean _closed;
    private InputContext _inputContext;
    private JavaType _objectContext;
    private IntrospectionResults _introspectionResults;
    private JsonToken _currentToken;
    private JsonToken _lastClearedToken;
    private long _currentInputCount;
    private long _currentTokenLocation;

    public MessagePackParser(IOContext ctxt, ObjectCodec codec, EnumSet<MessagePackFactory.Feature> msgPackFeatures, EnumSet<Feature> parserFeatures, InputStream in) {
        _ioContext = ctxt;
        _objectCodec = codec;
        _msgPackFeatures = msgPackFeatures;
        _parserFeatures = parserFeatures;
        _inputStream = in;
        _inputContext = new InputContext();
    }

    void setObjectContext(JavaType objectContext, IntrospectionResults introspectionResults) {
        _objectContext = objectContext;
        _introspectionResults = introspectionResults;
    }

    public MessagePackParser enable(MessagePackFactory.Feature f) {
        _msgPackFeatures.add(f);
        return this;
    }

    public MessagePackParser disable(MessagePackFactory.Feature f) {
        _msgPackFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(MessagePackFactory.Feature f) {
        return _msgPackFeatures.contains(f);
    }

    public MessagePackParser enable(Feature f) {
        _parserFeatures.add(f);
        return this;
    }

    public MessagePackParser disable(Feature f) {
        _parserFeatures.remove(f);
        return this;
    }

    public boolean isEnabled(Feature f) {
        return _parserFeatures.contains(f);
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
        return _objectCodec;
    }

    /**
     * Setter that allows defining {@link ObjectCodec} associated with this
     * parser, if any. Codec is used by {@link #readValueAs(Class)}
     * method (and its variants).
     *
     * @since 1.3
     */
    @Override
    public void setCodec(ObjectCodec oc) {
        _objectCodec = oc;
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
        if (!_closed) {
            _closed = true;
            if (_ioContext.isResourceManaged())
                _inputStream.close();
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
        _currentToken = _inputContext.nextToken();
        _inputContext = _inputContext.nextInputContext();
        return _currentToken;
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
        if (_inputContext.inRoot() || _inputContext.getEntryCount() > 0)
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
        return _closed;
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
        return _currentToken;
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
        return _currentToken != null;
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
        return _inputContext.getCurrentName();
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
        return _inputContext;
    }

    /**
     * Method that return the <b>starting</b> location of the current
     * token; that is, position of the first character from input
     * that starts the current token.
     */
    @Override
    public JsonLocation getTokenLocation() {
        return new JsonLocation(_ioContext.getSourceReference(), _currentTokenLocation, -1, 0, 0);
    }

    /**
     * Method that returns location of the last processed character;
     * usually for error reporting purposes.
     */
    @Override
    public JsonLocation getCurrentLocation() {
        return new JsonLocation(_ioContext.getSourceReference(), _currentInputCount, -1, 0, 0);
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
        if (_currentToken != null) {
            _lastClearedToken = _currentToken;
            _currentToken = null;
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
        return _lastClearedToken;
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
        ((MapInputContext) _inputContext)._currentName = name;
    }

    /**
     * Method for accessing textual representation of the current token;
     * if no current token (before first call to {@link #nextToken}, or
     * after encountering end-of-input), returns null.
     * Method can be called for any token type.
     */
    @Override
    public String getText() throws IOException, JsonParseException {
        if (_inputContext._stringValue != null)
            return _inputContext._stringValue;
        if (_inputContext._valueType == null)
            return null;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0) {
                _inputContext._stringValue = String.valueOf(_inputContext._intValue);
            } else {
                //...unsigned 64bit number
                _inputContext._stringValue = String.valueOf(_inputContext._intValue);
            }
            break;
        case BOOLEAN:
            _inputContext._stringValue = String.valueOf(_inputContext._boolValue);
            break;
        case FLOAT:
            _inputContext._stringValue = String.valueOf(_inputContext._floatValue);
            break;
        case DOUBLE:
            _inputContext._stringValue = String.valueOf(_inputContext._doubleValue);
            break;
        case BYTES:
            _inputContext._stringValue = new String(_inputContext._bytesValue, "UTF-8");
            break;
        }
        return _inputContext._stringValue;
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
        if (_inputContext._valueType == null)
            return null;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValue >= Integer.MIN_VALUE && _inputContext._intValue <= Integer.MAX_VALUE)
                return Integer.valueOf((int) _inputContext._intValue);
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return Long.valueOf(_inputContext._intValue);
            //... unsigned 64bit number
            return Long.valueOf(_inputContext._intValue);
        case FLOAT:
            return _inputContext._floatValue;
        case DOUBLE:
            return _inputContext._doubleValue;
        }
        return null;
    }

    /**
     * If current token is of type 
     * {@link JsonToken#VALUE_NUMBER_INT} or
     * {@link JsonToken#VALUE_NUMBER_FLOAT}, returns
     * one of {@link NumberType} constants; otherwise returns null.
     */
    @Override
    public JsonParser.NumberType getNumberType() throws IOException, JsonParseException {
        if (_inputContext._valueType == null)
            return null;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValue >= Integer.MIN_VALUE && _inputContext._intValue <= Integer.MAX_VALUE)
                return JsonParser.NumberType.INT;
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return JsonParser.NumberType.LONG;
            //... unsigned 64bit number: JsonParser.NumberType.BIG_INTEGER;
            return JsonParser.NumberType.LONG;
        case FLOAT:
            return JsonParser.NumberType.FLOAT;
        case DOUBLE:
            return JsonParser.NumberType.DOUBLE;
        }
        return null;
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
        if (_inputContext._valueType == null)
            return 0;
        switch (_inputContext._valueType) {
        case INT:
            return (int) _inputContext._intValue;
        case FLOAT:
            return (int) _inputContext._floatValue;
        case DOUBLE:
            return (int) _inputContext._doubleValue;
        }
        return 0;
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
        if (_inputContext._valueType == null)
            return 0;
        switch (_inputContext._valueType) {
        case INT:
            return _inputContext._intValue;
        case FLOAT:
            return (long) _inputContext._floatValue;
        case DOUBLE:
            return (long) _inputContext._doubleValue;
        }
        return 0;
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
        if (_inputContext._valueType == null)
            return null;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return BigInteger.valueOf(_inputContext._intValue);
            //... unsigned 64bit number
            return BigInteger.valueOf(_inputContext._intValue);
        case FLOAT:
            return BigDecimal.valueOf((double) _inputContext._floatValue).toBigInteger();
        case DOUBLE:
            return BigDecimal.valueOf(_inputContext._doubleValue).toBigInteger();
        }
        return null;
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
        if (_inputContext._valueType == null)
            return 0.0f;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return (float) _inputContext._intValue;
            //... unsigned 64bit number
            return (float) _inputContext._intValue;
        case FLOAT:
            return _inputContext._floatValue;
        case DOUBLE:
            return (float) _inputContext._doubleValue;
        }
        return 0.0f;
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
        if (_inputContext._valueType == null)
            return 0.0;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return (double) _inputContext._intValue;
            //... unsigned 64bit number
            return (double) _inputContext._intValue;
        case FLOAT:
            return (double) _inputContext._floatValue;
        case DOUBLE:
            return _inputContext._doubleValue;
        }
        return 0.0;
    }

    /**
     * Numeric accessor that can be called when the current
     * token is of type {@link JsonToken#VALUE_NUMBER_FLOAT} or
     * {@link JsonToken#VALUE_NUMBER_INT}. No under/overflow exceptions
     * are ever thrown.
     */
    @Override
    public BigDecimal getDecimalValue() throws IOException, JsonParseException {
        if (_inputContext._valueType == null)
            return null;
        switch (_inputContext._valueType) {
        case INT:
            if (_inputContext._intValueSigned || _inputContext._intValue >= 0)
                return BigDecimal.valueOf(_inputContext._intValue);
            //... unsigned 64bit number
            return BigDecimal.valueOf(_inputContext._intValue);
        case FLOAT:
            return BigDecimal.valueOf((double) _inputContext._floatValue);
        case DOUBLE:
            return BigDecimal.valueOf(_inputContext._doubleValue);
        }
        return null;
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
        return _inputContext._bytesValue;
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
        String text = getText();
        return text == null ? defaultValue : text;
    }
}
