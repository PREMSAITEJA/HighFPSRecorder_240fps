package com.example.highfps;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * StorageManager: Manages frame output folder creation and path management.
 *
 * Uses app-specific external media storage (scoped storage compatible):
 * /Android/media/com.example.highfps/frames/
 */
public class StorageManager {
    private static final String TAG = "StorageManager";
    private static final String FRAMES_FOLDER = "frames";

    private final Context context;
    private File framesDir;

    public StorageManager(Context context) {
        this.context = context;
    }

    /**
     * Initialize frame storage directory.
     * Creates folder if it doesn't exist.
     *
     * @return true if directory is ready, false if creation failed
     */
    public boolean initFramesDirectory() {
        try {
            // Get app-specific external media storage (no runtime permission needed)
            File[] mediaDirs = context.getExternalMediaDirs();

            if (mediaDirs == null) {
                Log.e(TAG, "getExternalMediaDirs() returned null");
                return false;
            }
            if (mediaDirs.length == 0 || mediaDirs[0] == null) {
                Log.e(TAG, "No external media directory available");
                return false;
            }

            File externalMediaDir = mediaDirs[0];
            framesDir = new File(externalMediaDir, FRAMES_FOLDER);

            // Create directory if it doesn't exist
            if (!framesDir.exists()) {
                boolean created = framesDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create frames directory: " + framesDir.getAbsolutePath());
                    return false;
                }
                Log.d(TAG, "Created frames directory: " + framesDir.getAbsolutePath());
            }

            // Verify it's writable
            if (!framesDir.canWrite()) {
                Log.e(TAG, "Frames directory is not writable");
                return false;
            }

            Log.i(TAG, "Frame storage ready: " + framesDir.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error initializing frames directory", e);
            return false;
        }
    }

    /**
     * Get frames directory.
     */
    public File getFramesDirectory() {
        return framesDir;
    }

    /**
     * Get available storage space in GB.
     */
    public double getAvailableStorageGB() {
        if (framesDir == null) {
            return 0;
        }

        try {
            long availableBytes = framesDir.getFreeSpace();
            return availableBytes / (1024.0 * 1024.0 * 1024.0);
        } catch (Exception e) {
            Log.e(TAG, "Error checking available space", e);
            return 0;
        }
    }

    /**
     * Estimate recording time remaining based on available space.
     * Assumes ~528 MB/s throughput (240 fps × 2.2 MB/frame).
     *
     * @return estimated seconds of recording possible
     */
    public long estimateRecordingTimeSeconds() {
        double availableGB = getAvailableStorageGB();
        double bytesPerSecond = 528 * 1024 * 1024;  // 528 MB/s
        double availableBytes = availableGB * 1024 * 1024 * 1024;

        return (long) (availableBytes / bytesPerSecond);
    }

    /**
     * Get total frame count in frames directory.
     */
    public int getFrameCount() {
        if (framesDir == null) {
            return 0;
        }

        File[] files = framesDir.listFiles((dir, name) -> name.endsWith(".tiff"));
        return files != null ? files.length : 0;
    }

    /**
     * Clear frames directory (delete all TIFF files).
     * Use with caution!
     */
    public boolean clearFramesDirectory() {
        if (framesDir == null) {
            return false;
        }

        try {
            File[] files = framesDir.listFiles((dir, name) -> name.endsWith(".tiff"));

            if (files == null) {
                return true;  // No files to delete
            }

            int deletedCount = 0;
            for (File file : files) {
                if (file.delete()) {
                    deletedCount++;
                }
            }

            Log.i(TAG, "Deleted " + deletedCount + " TIFF files");
            return deletedCount == files.length;

        } catch (Exception e) {
            Log.e(TAG, "Error clearing frames directory", e);
            return false;
        }
    }

    /**
     * Get human-readable storage status.
     */
    public String getStorageStatus() {
        if (framesDir == null) {
            return "Storage not initialized";
        }

        double availableGB = getAvailableStorageGB();
        long recordTimeSeconds = estimateRecordingTimeSeconds();
        int frameCount = getFrameCount();

        long minutes = recordTimeSeconds / 60;
        long seconds = recordTimeSeconds % 60;

        return String.format(
            "Available: %.1f GB (max ~%d:%02d recording)\nFrames: %d",
            availableGB,
            minutes,
            seconds,
            frameCount
        );
    }
}

