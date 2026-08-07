package io.github.ghosthack.libjxlffm;

import io.github.ghosthack.libjxlffm.libjxl.JxlBasicInfo;
import io.github.ghosthack.libjxlffm.libjxl.JxlColorEncoding;
import io.github.ghosthack.libjxlffm.libjxl.JxlPixelFormat;
import io.github.ghosthack.libjxlffm.libjxl.LibJxl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end proof that bundled libjxl and Brotli load, encode, and decode. */
class SmokeTest {
    private static final int WIDTH = 8;
    private static final int HEIGHT = 6;

    @Test
    void bundledNativesReportExpectedVersion() {
        assertEquals(12_000, LibJxl.JxlDecoderVersion());
        assertEquals(12_000, LibJxl.JxlEncoderVersion());
        assertTrue(LibJxl.JxlThreadParallelRunnerDefaultNumWorkerThreads() > 0);
        assertTrue(LibjxlLibs.lookup().find("BrotliGetDictionary").isPresent(),
                "bundled Brotli common library");
        assertTrue(LibjxlLibs.lookup().find("BrotliDecoderVersion").isPresent(),
                "bundled Brotli decoder library");
        assertTrue(LibjxlLibs.lookup().find("BrotliEncoderVersion").isPresent(),
                "bundled Brotli encoder library");
    }

    @Test
    void losslessRgbRoundTrip() {
        byte[] pixels = testPixels();
        byte[] encoded = encode(pixels);
        assertTrue(encoded.length > 10, "non-empty JPEG XL codestream");

        byte[] signature = encoded.length > 12
                ? java.util.Arrays.copyOf(encoded, 12) : encoded;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment signatureBytes = arena.allocate(signature.length);
            signatureBytes.copyFrom(MemorySegment.ofArray(signature));
            assertNotEquals(LibJxl.JXL_SIG_INVALID(),
                    LibJxl.JxlSignatureCheck(signatureBytes, signature.length));
        }
        assertArrayEquals(pixels, decode(encoded));
    }

    private static byte[] encode(byte[] pixels) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment encoder = LibJxl.JxlEncoderCreate(MemorySegment.NULL);
            MemorySegment runner = LibJxl.JxlThreadParallelRunnerCreate(MemorySegment.NULL, 2);
            assertNotEquals(MemorySegment.NULL, encoder);
            assertNotEquals(MemorySegment.NULL, runner);
            try {
                checkEncoder(LibJxl.JxlEncoderSetParallelRunner(encoder,
                        LibJxl.JxlThreadParallelRunner$address(), runner), "set parallel runner");

                MemorySegment info = arena.allocate(JxlBasicInfo.layout());
                LibJxl.JxlEncoderInitBasicInfo(info);
                JxlBasicInfo.xsize(info, WIDTH);
                JxlBasicInfo.ysize(info, HEIGHT);
                JxlBasicInfo.bits_per_sample(info, 8);
                JxlBasicInfo.num_color_channels(info, 3);
                JxlBasicInfo.num_extra_channels(info, 0);
                // Pixel-exact lossless requires preserving the original color profile.
                JxlBasicInfo.uses_original_profile(info, LibJxl.JXL_TRUE());
                checkEncoder(LibJxl.JxlEncoderSetBasicInfo(encoder, info), "set basic info");

                MemorySegment color = arena.allocate(JxlColorEncoding.layout());
                LibJxl.JxlColorEncodingSetToSRGB(color, LibJxl.JXL_FALSE());
                checkEncoder(LibJxl.JxlEncoderSetColorEncoding(encoder, color), "set sRGB");

                MemorySegment format = pixelFormat(arena);
                MemorySegment settings = LibJxl.JxlEncoderFrameSettingsCreate(
                        encoder, MemorySegment.NULL);
                assertNotEquals(MemorySegment.NULL, settings);
                checkEncoder(LibJxl.JxlEncoderSetFrameLossless(settings, LibJxl.JXL_TRUE()),
                        "set lossless");
                MemorySegment input = arena.allocate(pixels.length);
                input.copyFrom(MemorySegment.ofArray(pixels));
                checkEncoder(LibJxl.JxlEncoderAddImageFrame(
                        settings, format, input, pixels.length), "add image frame");
                LibJxl.JxlEncoderCloseInput(encoder);

                ByteArrayOutputStream encoded = new ByteArrayOutputStream();
                MemorySegment nextOut = arena.allocate(ValueLayout.ADDRESS);
                MemorySegment availOut = arena.allocate(ValueLayout.JAVA_LONG);
                int status;
                do {
                    MemorySegment chunk = arena.allocate(256);
                    nextOut.set(ValueLayout.ADDRESS, 0, chunk);
                    availOut.set(ValueLayout.JAVA_LONG, 0, chunk.byteSize());
                    status = LibJxl.JxlEncoderProcessOutput(encoder, nextOut, availOut);
                    long used = chunk.byteSize() - availOut.get(ValueLayout.JAVA_LONG, 0);
                    encoded.writeBytes(chunk.asSlice(0, used).toArray(ValueLayout.JAVA_BYTE));
                } while (status == LibJxl.JXL_ENC_NEED_MORE_OUTPUT());
                checkEncoder(status, "process output");
                return encoded.toByteArray();
            } finally {
                LibJxl.JxlThreadParallelRunnerDestroy(runner);
                LibJxl.JxlEncoderDestroy(encoder);
            }
        }
    }

    private static byte[] decode(byte[] encoded) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment decoder = LibJxl.JxlDecoderCreate(MemorySegment.NULL);
            MemorySegment runner = LibJxl.JxlThreadParallelRunnerCreate(MemorySegment.NULL, 2);
            assertNotEquals(MemorySegment.NULL, decoder);
            assertNotEquals(MemorySegment.NULL, runner);
            try {
                checkDecoder(LibJxl.JxlDecoderSetParallelRunner(decoder,
                        LibJxl.JxlThreadParallelRunner$address(), runner), "set parallel runner");
                checkDecoder(LibJxl.JxlDecoderSubscribeEvents(decoder,
                        LibJxl.JXL_DEC_BASIC_INFO() | LibJxl.JXL_DEC_FULL_IMAGE()),
                        "subscribe events");
                MemorySegment input = arena.allocate(encoded.length);
                input.copyFrom(MemorySegment.ofArray(encoded));
                checkDecoder(LibJxl.JxlDecoderSetInput(decoder, input, encoded.length), "set input");
                LibJxl.JxlDecoderCloseInput(decoder);

                MemorySegment format = pixelFormat(arena);
                MemorySegment size = arena.allocate(ValueLayout.JAVA_LONG);
                MemorySegment output = MemorySegment.NULL;
                boolean fullImage = false;
                for (;;) {
                    int status = LibJxl.JxlDecoderProcessInput(decoder);
                    if (status == LibJxl.JXL_DEC_BASIC_INFO()) {
                        MemorySegment info = arena.allocate(JxlBasicInfo.layout());
                        checkDecoder(LibJxl.JxlDecoderGetBasicInfo(decoder, info), "get basic info");
                        assertEquals(WIDTH, JxlBasicInfo.xsize(info));
                        assertEquals(HEIGHT, JxlBasicInfo.ysize(info));
                    } else if (status == LibJxl.JXL_DEC_NEED_IMAGE_OUT_BUFFER()) {
                        checkDecoder(LibJxl.JxlDecoderImageOutBufferSize(decoder, format, size),
                                "get output size");
                        long outputSize = size.get(ValueLayout.JAVA_LONG, 0);
                        assertEquals((long) WIDTH * HEIGHT * 3, outputSize);
                        output = arena.allocate(outputSize);
                        checkDecoder(LibJxl.JxlDecoderSetImageOutBuffer(
                                decoder, format, output, outputSize), "set output buffer");
                    } else if (status == LibJxl.JXL_DEC_FULL_IMAGE()) {
                        fullImage = true;
                    } else if (status == LibJxl.JXL_DEC_SUCCESS()) {
                        assertTrue(fullImage, "received a complete frame");
                        assertNotEquals(MemorySegment.NULL, output);
                        return output.toArray(ValueLayout.JAVA_BYTE);
                    } else {
                        throw new AssertionError("unexpected decoder status: " + status);
                    }
                }
            } finally {
                LibJxl.JxlThreadParallelRunnerDestroy(runner);
                LibJxl.JxlDecoderDestroy(decoder);
            }
        }
    }

    private static MemorySegment pixelFormat(Arena arena) {
        MemorySegment format = arena.allocate(JxlPixelFormat.layout());
        JxlPixelFormat.num_channels(format, 3);
        JxlPixelFormat.data_type(format, LibJxl.JXL_TYPE_UINT8());
        JxlPixelFormat.endianness(format, LibJxl.JXL_NATIVE_ENDIAN());
        JxlPixelFormat.align(format, 0);
        return format;
    }

    private static byte[] testPixels() {
        byte[] pixels = new byte[WIDTH * HEIGHT * 3];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 3;
                pixels[offset] = (byte) (x * 31);
                pixels[offset + 1] = (byte) (y * 47);
                pixels[offset + 2] = (byte) ((x + y) * 19);
            }
        }
        return pixels;
    }

    private static void checkEncoder(int status, String operation) {
        assertEquals(LibJxl.JXL_ENC_SUCCESS(), status,
                operation + " failed with encoder status " + status);
    }

    private static void checkDecoder(int status, String operation) {
        assertEquals(LibJxl.JXL_DEC_SUCCESS(), status,
                operation + " failed with decoder status " + status);
    }
}
