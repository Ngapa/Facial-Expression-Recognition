package com.gtek.fren.executorch;
import com.gtek.fren.executorch.annotations.Experimental;

@Experimental
public enum DType {
    // NOTE: "jniCode" must be kept in sync with scalar_type.h.
    // NOTE: Never serialize "jniCode", because it can change between releases.

    /** Code for dtype ScalarType::Byte */
    UINT8(0),
    /** Code for dtype ScalarType::Char */
    INT8(1),
    /** Code for dtype ScalarType::Short */
    INT16(2),
    /** Code for dtype ScalarType::Int */
    INT32(3),
    /** Code for dtype ScalarType::Long */
    INT64(4),
    /** Code for dtype ScalarType::Half */
    HALF(5),
    /** Code for dtype ScalarType::Float */
    FLOAT(6),
    /** Code for dtype ScalarType::Double */
    DOUBLE(7),
    /** Code for dtype ScalarType::ComplexHalf */
    COMPLEX_HALF(8),
    /** Code for dtype ScalarType::ComplexFloat */
    COMPLEX_FLOAT(9),
    /** Code for dtype ScalarType::ComplexDouble */
    COMPLEX_DOUBLE(10),
    /** Code for dtype ScalarType::Bool */
    BOOL(11),
    /** Code for dtype ScalarType::QInt8 */
    QINT8(12),
    /** Code for dtype ScalarType::QUInt8 */
    QUINT8(13),
    /** Code for dtype ScalarType::QInt32 */
    QINT32(14),
    /** Code for dtype ScalarType::BFloat16 */
    BFLOAT16(15),
    /** Code for dtype ScalarType::QUInt4x2 */
    QINT4X2(16),
    /** Code for dtype ScalarType::QUInt2x4 */
    QINT2X4(17),
    /** Code for dtype ScalarType::Bits1x8 */
    BITS1X8(18),
    /** Code for dtype ScalarType::Bits2x4 */
    BITS2X4(19),
    /** Code for dtype ScalarType::Bits4x2 */
    BITS4X2(20),
    /** Code for dtype ScalarType::Bits8 */
    BITS8(21),
    /** Code for dtype ScalarType::Bits16 */
    BITS16(22),
    ;

    final int jniCode;

    DType(int jniCode) {
        this.jniCode = jniCode;
    }
}