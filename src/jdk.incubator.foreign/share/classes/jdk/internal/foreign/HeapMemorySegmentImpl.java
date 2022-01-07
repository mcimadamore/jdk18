/*
 *  Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import jdk.incubator.foreign.MemorySegment;
import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Implementation for heap memory segments. An heap memory segment is composed by an offset and
 * a base object (typically an array). To enhance performances, the access to the base object needs to feature
 * sharp type information, as well as sharp null-check information. For this reason, many concrete subclasses
 * of {@link HeapMemorySegmentImpl} are defined (e.g. {@link OfFloat}, so that each subclass can override the
 * {@link HeapMemorySegmentImpl#base()} method so that it returns an array of the correct (sharp) type.
 */
public class HeapMemorySegmentImpl<H> extends AbstractMemorySegmentImpl {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final int BYTE_ARR_BASE = UNSAFE.arrayBaseOffset(byte[].class);

    private static final long MAX_ALIGN_1 = 1;
    private static final long MAX_ALIGN_2 = 2;
    private static final long MAX_ALIGN_4 = 4;
    private static final long MAX_ALIGN_8 = 8;

    static final int BYTE_KIND = 1;
    static final int CHAR_KIND = 2;
    static final int SHORT_KIND = 3;
    static final int INT_KIND = 4;
    static final int FLOAT_KIND = 5;
    static final int LONG_KIND = 6;
    static final int DOUBLE_KIND = 7;

    final long offset;
    final H base;
    final int kind;

    @ForceInline
    HeapMemorySegmentImpl(int kind, long offset, H base, long length, int mask) {
        super(length, mask, ResourceScopeImpl.GLOBAL);
        this.kind = kind;
        this.offset = offset;
        this.base = base;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    final H base() {
        switch (kind) {
            case BYTE_KIND:
                return (H) byte[].class.cast(Objects.requireNonNull(base));
            case CHAR_KIND:
                return (H) char[].class.cast(Objects.requireNonNull(base));
            case SHORT_KIND:
                return (H)short[].class.cast(Objects.requireNonNull(base));
            case INT_KIND:
                return (H)int[].class.cast(Objects.requireNonNull(base));
            case FLOAT_KIND:
                return (H) float[].class.cast(Objects.requireNonNull(base));
            case LONG_KIND:
                return (H)long[].class.cast(Objects.requireNonNull(base));
            case DOUBLE_KIND:
                return (H)double[].class.cast(Objects.requireNonNull(base));
        }
        throw new AssertionError();
    }

    @Override
    long min() {
        return offset;
    }

    @Override
    final HeapMemorySegmentImpl<H> dup(long offset, long size, int mask, ResourceScopeImpl scope) {
        return new HeapMemorySegmentImpl<>(kind, this.offset + offset, base, size, mask);
    }

    @Override
    public long maxAlignMask() {
        switch (kind) {
            case BYTE_KIND:
                return MAX_ALIGN_1;
            case CHAR_KIND:
            case SHORT_KIND:
                return MAX_ALIGN_2;
            case INT_KIND:
            case FLOAT_KIND:
                return MAX_ALIGN_4;
            case LONG_KIND:
            case DOUBLE_KIND:
                return MAX_ALIGN_8;
        }
        throw new AssertionError();
    }

    @Override
    ByteBuffer makeByteBuffer() {
        if (!(base() instanceof byte[])) {
            throw new UnsupportedOperationException("Not an address to an heap-allocated byte array");
        }
        JavaNioAccess nioAccess = SharedSecrets.getJavaNioAccess();
        return nioAccess.newHeapByteBuffer((byte[]) base(), (int)min() - BYTE_ARR_BASE, (int) byteSize(), null);
    }

    // factories

    public static class OfByte {

        public static MemorySegment fromArray(byte[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_BYTE_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(BYTE_KIND, Unsafe.ARRAY_BYTE_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfChar {

        public static MemorySegment fromArray(char[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_CHAR_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(CHAR_KIND, Unsafe.ARRAY_CHAR_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfShort {

        public static MemorySegment fromArray(short[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_SHORT_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(SHORT_KIND, Unsafe.ARRAY_SHORT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfInt {

        public static MemorySegment fromArray(int[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_INT_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(INT_KIND, Unsafe.ARRAY_INT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfLong {

        public static MemorySegment fromArray(long[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_LONG_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(LONG_KIND, Unsafe.ARRAY_LONG_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfFloat {

        public static MemorySegment fromArray(float[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(FLOAT_KIND, Unsafe.ARRAY_FLOAT_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

    public static class OfDouble {

        public static MemorySegment fromArray(double[] arr) {
            Objects.requireNonNull(arr);
            long byteSize = (long)arr.length * Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            return new HeapMemorySegmentImpl<>(DOUBLE_KIND, Unsafe.ARRAY_DOUBLE_BASE_OFFSET, arr, byteSize, defaultAccessModes(byteSize));
        }
    }

}
