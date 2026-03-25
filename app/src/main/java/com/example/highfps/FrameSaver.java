package com.example.highfps;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FrameSaver: Manages frame storage for 240 fps raw frame capture.
 *
 * Creates a session folder and saves each frame as a TIFF with sequential numbering
 * and timestamp in the filename. Converts YUV_420_888 to grayscale bitmap.
 */
public class FrameSaver {
    private static final String TAG = "FrameSaver";

    private final File sessionDir;
    private final AtomicInteger frameCount;

    public FrameSaver(Context context) {
        this.frameCount = new AtomicInteger(0);

        // Use app external media root: /storage/emulated/0/Android/media/<package>/
        File[] mediaDirs = context.getExternalMediaDirs();
        if (mediaDirs == null) {
            throw new IllegalStateException("getExternalMediaDirs() returned null");
        }
        if (mediaDirs.length == 0 || mediaDirs[0] == null) {
            throw new IllegalStateException("No external media directory available");
        }
        File baseDir = mediaDirs[0];

        // Create session directory
        String sessionName = "session_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        sessionDir = new File(baseDir, sessionName);

        if (!sessionDir.exists()) {
            sessionDir.mkdirs();
            Log.d(TAG, "Session folder created: " + sessionDir.getAbsolutePath());
        }
    }

    /**
     * Save a single frame as a TIFF file with timestamp in filename.
     */
    public boolean saveFrame(Image image) {
        try {
            int frameIndex = frameCount.incrementAndGet();
            long timestamp = System.currentTimeMillis();

            // Convert YUV_420_888 to grayscale bitmap
            Bitmap bitmap = convertYuvToGrayscale(image);

            if (bitmap == null) {
                Log.e(TAG, "Failed to convert image to bitmap");
                return false;
            }

            // Generate filename with index and timestamp
            String filename = String.format(
                    Locale.US,
                    "frame_%05d_%d.tiff",
                    frameIndex,
                    timestamp
            );

            File outputFile = new File(sessionDir, filename);

            // Save as TIFF
            boolean savedSuccess = saveBitmapAsRawTiff(bitmap, outputFile);

            if (savedSuccess) {
                Log.d(TAG, "Frame " + frameIndex + " saved: " + filename);
            } else {
                Log.e(TAG, "Failed to save frame " + frameIndex);
            }

            bitmap.recycle();
            return savedSuccess;

        } catch (Exception e) {
            Log.e(TAG, "Error saving frame", e);
            return false;
        } finally {
            image.close();
        }
    }

    /**
     * Convert YUV_420_888 image to grayscale bitmap (8-bit).
     */
    private Bitmap convertYuvToGrayscale(Image image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            Image.Plane yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            int rowStride = yPlane.getRowStride();

            byte[] compactY = new byte[width * height];
            for (int row = 0; row < height; row++) {
                int srcPos = row * rowStride;
                yBuffer.position(srcPos);
                yBuffer.get(compactY, row * width, width);
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(compactY));
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV to grayscale", e);
            return null;
        }
    }

    /**
     * Save bitmap as a raw TIFF file.
     */
    private boolean saveBitmapAsRawTiff(Bitmap bitmap, File outputFile) {
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            byte[] pixelData = new byte[width * height];
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(pixelData));

            // Write minimal TIFF header
            writeTiffHeader(fos, width, height, pixelData);

            Log.d(TAG, "Bitmap saved as TIFF: " + outputFile.getName());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error writing TIFF file", e);
            return false;
        }
    }

    /**
     * Write a minimal TIFF header for 8-bit grayscale image.
     */
    private void writeTiffHeader(FileOutputStream fos, int width, int height, byte[] pixelData)
            throws IOException {
        // TIFF Header
        fos.write(0x49); // 'I' - Little Endian
        fos.write(0x49); // 'I'
        writeShortLE(fos, (short) 42); // TIFF version
        writeIntLE(fos, 8); // Offset to first IFD

        // Image File Directory (IFD)
        writeShortLE(fos, (short) 9); // Number of tags

        // Tag 254: NewSubfileType
        writeTag(fos, 254, 3, 1, 0);

        // Tag 256: ImageWidth
        writeTag(fos, 256, 3, 1, width);

        // Tag 257: ImageLength (height)
        writeTag(fos, 257, 3, 1, height);

        // Tag 258: BitsPerSample (8-bit)
        writeTag(fos, 258, 3, 1, 8);

        // Tag 259: Compression (1 = uncompressed)
        writeTag(fos, 259, 3, 1, 1);

        // Tag 262: PhotometricInterpretation (1 = BlackIsZero)
        writeTag(fos, 262, 3, 1, 1);

        // Tag 273: StripOffsets
        int stripOffset = 8 + 2 + (9 * 12) + 4;
        writeTag(fos, 273, 4, 1, stripOffset);

        // Tag 277: SamplesPerPixel (1 = grayscale)
        writeTag(fos, 277, 3, 1, 1);

        // Tag 279: StripByteCounts
        writeTag(fos, 279, 4, 1, pixelData.length);

        // Next IFD offset (0 = no more IFDs)
        writeIntLE(fos, 0);

        // Write pixel data
        fos.write(pixelData);
    }

    /**
     * Write a TIFF IFD tag entry.
     */
    private void writeTag(FileOutputStream fos, int tag, int type, int count, int value)
            throws IOException {
        writeShortLE(fos, (short) tag);
        writeShortLE(fos, (short) type);
        writeIntLE(fos, count);
        writeIntLE(fos, value);
    }

    /**
     * Write a 16-bit value in little-endian format.
     */
    private void writeShortLE(FileOutputStream fos, short value) throws IOException {
        fos.write((value) & 0xFF);
        fos.write((value >> 8) & 0xFF);
    }

    /**
     * Write a 32-bit value in little-endian format.
     */
    private void writeIntLE(FileOutputStream fos, int value) throws IOException {
        fos.write((value) & 0xFF);
        fos.write((value >> 8) & 0xFF);
        fos.write((value >> 16) & 0xFF);
        fos.write((value >> 24) & 0xFF);
    }

    /**
     * Get the total number of frames saved.
     */
    public int getFrameCount() {
        return frameCount.get();
    }

    /**
     * Get the session directory path.
     */
    public File getSessionDir() {
        return sessionDir;
    }
}
