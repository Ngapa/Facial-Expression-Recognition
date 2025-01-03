package com.gtek.fren.executorch;

import com.facebook.jni.annotations.DoNotStrip;
import com.gtek.fren.executorch.annotations.Experimental;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;


@Experimental
@DoNotStrip
public class EValue {
    private static final int TYPE_CODE_NONE = 0;

    private static final int TYPE_CODE_TENSOR = 1;
    private static final int TYPE_CODE_STRING = 2;
    private static final int TYPE_CODE_DOUBLE = 3;
    private static final int TYPE_CODE_INT = 4;
    private static final int TYPE_CODE_BOOL = 5;

    private String[] TYPE_NAMES = {
            "None", "Tensor", "String", "Double", "Int", "Bool",
    };

    @DoNotStrip private final int mTypeCode;
    @DoNotStrip private Object mData;

    @DoNotStrip
    private EValue(int typeCode) {
        this.mTypeCode = typeCode;
    }

    @DoNotStrip
    public boolean isNone() {
        return TYPE_CODE_NONE == this.mTypeCode;
    }

    @DoNotStrip
    public boolean isTensor() {
        return TYPE_CODE_TENSOR == this.mTypeCode;
    }

    @DoNotStrip
    public boolean isBool() {
        return TYPE_CODE_BOOL == this.mTypeCode;
    }

    @DoNotStrip
    public boolean isInt() {
        return TYPE_CODE_INT == this.mTypeCode;
    }

    @DoNotStrip
    public boolean isDouble() {
        return TYPE_CODE_DOUBLE == this.mTypeCode;
    }

    @DoNotStrip
    public boolean isString() {
        return TYPE_CODE_STRING == this.mTypeCode;
    }

    /** Creates a new {@code EValue} of type {@code Optional} that contains no value. */
    @DoNotStrip
    public static EValue optionalNone() {
        return new EValue(TYPE_CODE_NONE);
    }

    /** Creates a new {@code EValue} of type {@code Tensor}. */
    @DoNotStrip
    public static EValue from(Tensor tensor) {
        final EValue iv = new EValue(TYPE_CODE_TENSOR);
        iv.mData = tensor;
        return iv;
    }

    /** Creates a new {@code EValue} of type {@code bool}. */
    @DoNotStrip
    public static EValue from(boolean value) {
        final EValue iv = new EValue(TYPE_CODE_BOOL);
        iv.mData = value;
        return iv;
    }

    /** Creates a new {@code EValue} of type {@code int}. */
    @DoNotStrip
    public static EValue from(long value) {
        final EValue iv = new EValue(TYPE_CODE_INT);
        iv.mData = value;
        return iv;
    }

    /** Creates a new {@code EValue} of type {@code double}. */
    @DoNotStrip
    public static EValue from(double value) {
        final EValue iv = new EValue(TYPE_CODE_DOUBLE);
        iv.mData = value;
        return iv;
    }

    /** Creates a new {@code EValue} of type {@code str}. */
    @DoNotStrip
    public static EValue from(String value) {
        final EValue iv = new EValue(TYPE_CODE_STRING);
        iv.mData = value;
        return iv;
    }

    @DoNotStrip
    public Tensor toTensor() {
        preconditionType(TYPE_CODE_TENSOR, mTypeCode);
        return (Tensor) mData;
    }

    @DoNotStrip
    public boolean toBool() {
        preconditionType(TYPE_CODE_BOOL, mTypeCode);
        return (boolean) mData;
    }

    @DoNotStrip
    public long toInt() {
        preconditionType(TYPE_CODE_INT, mTypeCode);
        return (long) mData;
    }

    @DoNotStrip
    public double toDouble() {
        preconditionType(TYPE_CODE_DOUBLE, mTypeCode);
        return (double) mData;
    }

    @DoNotStrip
    public String toStr() {
        preconditionType(TYPE_CODE_STRING, mTypeCode);
        return (String) mData;
    }

    private void preconditionType(int typeCodeExpected, int typeCode) {
        if (typeCode != typeCodeExpected) {
            throw new IllegalStateException(
                    String.format(
                            Locale.US,
                            "Expected EValue type %s, actual type %s",
                            getTypeName(typeCodeExpected),
                            getTypeName(typeCode)));
        }
    }

    private String getTypeName(int typeCode) {
        return typeCode >= 0 && typeCode < TYPE_NAMES.length ? TYPE_NAMES[typeCode] : "Unknown";
    }

    /**
     * Serializes an {@code EValue} into a byte array.
     *
     * @return The serialized byte array.
     * @apiNote This method is experimental and subject to change without notice.
     */
    public byte[] toByteArray() {
        if (isNone()) {
            return ByteBuffer.allocate(1).put((byte) TYPE_CODE_NONE).array();
        } else if (isTensor()) {
            Tensor t = toTensor();
            byte[] tByteArray = t.toByteArray();
            return ByteBuffer.allocate(1 + tByteArray.length)
                    .put((byte) TYPE_CODE_TENSOR)
                    .put(tByteArray)
                    .array();
        } else if (isBool()) {
            return ByteBuffer.allocate(2)
                    .put((byte) TYPE_CODE_BOOL)
                    .put((byte) (toBool() ? 1 : 0))
                    .array();
        } else if (isInt()) {
            return ByteBuffer.allocate(9).put((byte) TYPE_CODE_INT).putLong(toInt()).array();
        } else if (isDouble()) {
            return ByteBuffer.allocate(9).put((byte) TYPE_CODE_DOUBLE).putDouble(toDouble()).array();
        } else if (isString()) {
            return ByteBuffer.allocate(1 + toString().length())
                    .put((byte) TYPE_CODE_STRING)
                    .put(toString().getBytes())
                    .array();
        } else {
            throw new IllegalArgumentException("Unknown Tensor dtype");
        }
    }

    /**
     * Deserializes an {@code EValue} from a byte[].
     *
     * @param bytes The byte array to deserialize from.
     * @return The deserialized {@code EValue}.
     * @apiNote This method is experimental and subject to change without notice.
     */
    public static EValue fromByteArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (buffer == null) {
            throw new IllegalArgumentException("buffer cannot be null");
        }
        if (!buffer.hasRemaining()) {
            throw new IllegalArgumentException("invalid buffer");
        }
        int typeCode = buffer.get();
        switch (typeCode) {
            case TYPE_CODE_NONE:
                return new EValue(TYPE_CODE_NONE);
            case TYPE_CODE_TENSOR:
                byte[] bufferArray = buffer.array();
                return from(Tensor.fromByteArray(Arrays.copyOfRange(bufferArray, 1, bufferArray.length)));
            case TYPE_CODE_STRING:
                throw new IllegalArgumentException("TYPE_CODE_STRING is not supported");
            case TYPE_CODE_DOUBLE:
                return from(buffer.getDouble());
            case TYPE_CODE_INT:
                return from(buffer.getLong());
            case TYPE_CODE_BOOL:
                return from(buffer.get() != 0);
        }
        throw new IllegalArgumentException("invalid type code: " + typeCode);
    }
}