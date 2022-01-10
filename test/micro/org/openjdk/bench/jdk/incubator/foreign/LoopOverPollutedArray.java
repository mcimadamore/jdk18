/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jdk.incubator.foreign.MemoryLayout.PathElement.sequenceElement;
import static jdk.incubator.foreign.ValueLayout.JAVA_FLOAT;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign" })
public class LoopOverPollutedArray {

    static final int ELEM_SIZE = 1_000_000;
    static final int CARRIER_SIZE = (int) JAVA_INT.byteSize();
    static final int ALLOC_SIZE = ELEM_SIZE * CARRIER_SIZE;

    MemorySegment[] segments = new MemorySegment[3];
    IntBuffer[] buffers = new IntBuffer[3];
    ResourceScope scope = ResourceScope.newConfinedScope();

    @Param({"false", "true"})
    boolean pollute;

    @Setup
    public void setup() {
        segments[0] = MemorySegment.allocateNative(ALLOC_SIZE, scope);
        segments[1] = pollute ? MemorySegment.ofArray(new byte[ALLOC_SIZE]) : MemorySegment.allocateNative(ALLOC_SIZE, scope);
        segments[2] = pollute ? MemorySegment.ofArray(new int[ALLOC_SIZE / 4]) : MemorySegment.allocateNative(ALLOC_SIZE, scope);

        buffers[0] = ByteBuffer.allocateDirect(ALLOC_SIZE).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        buffers[1] = pollute ? ByteBuffer.wrap(new byte[ALLOC_SIZE]).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer() : ByteBuffer.allocateDirect(ALLOC_SIZE).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        buffers[2] = pollute ? IntBuffer.wrap(new int[ELEM_SIZE]) : ByteBuffer.allocateDirect(ALLOC_SIZE).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

        System.out.println(Stream.of(segments).map(Object::getClass).map(Class::getSimpleName).collect(Collectors.joining(", ")));
        System.out.println(Stream.of(buffers).map(Object::getClass).map(Class::getSimpleName).collect(Collectors.joining(", ")));
    }

    @TearDown
    public void tearDown() {
        scope.close();
    }

    @Benchmark
    public int segment_loop() {
        int sum = 0;
        for (int i = 0; i < segments.length; i++) {
            for (int j = 0; j < ELEM_SIZE; j++) {
                segments[i].setAtIndex(JAVA_INT, j, j + 1);
                int v = segments[i].getAtIndex(JAVA_INT, j);
                sum += v;
            }
        }
        return sum;
    }

    @Benchmark
    public int buffer_loop() {
        int sum = 0;
        for (int i = 0; i < buffers.length; i++) {
            for (int j = 0; j < ELEM_SIZE; j++) {
                buffers[i].put(j, j + 1);
                int v = buffers[i].get(j);
                sum += v;
            }
        }
        return sum;
    }
}
