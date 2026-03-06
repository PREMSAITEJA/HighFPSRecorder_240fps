package com.example.highfps;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * FrameProcessor: Handles ImageReader callbacks and manages background frame encoding.
 *
 * Uses a thread pool to encode frames asynchronously, preventing blocking of the camera
 * pipeline. Frames are queued if encoding can't keep up.
 */
public class FrameProcessor implements ImageReader.OnImageAvailableListener {
    private static final String TAG = "FrameProcessor";
    private static final int QUEUE_CAPACITY = 60;  // Buffer ~250ms of frames at 240fps
    private static final int NUM_THREADS = 4;      // Parallel encoding threads

    private final FrameSaver frameSaver;
    private final BlockingQueue<Image> frameQueue;
    private final ThreadPoolExecutor executorService;
    private volatile boolean isRecording;
    private volatile long droppedFrameCount;

    public FrameProcessor(FrameSaver frameSaver) {
        this.frameSaver = frameSaver;
        this.frameQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.isRecording = false;
        this.droppedFrameCount = 0;

        // Create thread pool for encoding
        this.executorService = new ThreadPoolExecutor(
            NUM_THREADS,           // Core threads
            NUM_THREADS,           // Max threads
            1,                     // Keep-alive time
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setName("FrameEncoder-" + t.getId());
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            }
        );
    }

    /**
     * Called by camera when a frame is available.
     * Queues frame for background encoding; drops if queue is full.
     */
    @Override
    public void onImageAvailable(ImageReader reader) {
        if (!isRecording) {
            Image image = reader.acquireNextImage();
            if (image != null) {
                image.close();
            }
            return;
        }

        Image image = reader.acquireNextImage();
        if (image == null) {
            return;
        }

        // Try to queue frame for processing
        boolean queued = frameQueue.offer(image);

        if (!queued) {
            Log.w(TAG, "Frame queue full, dropping frame");
            image.close();
            droppedFrameCount++;
        }
    }

    /**
     * Start encoding queued frames (call in separate thread to avoid blocking).
     */
    public void startEncodingFrames() {
        for (int i = 0; i < NUM_THREADS; i++) {
            executorService.execute(new FrameEncoderTask());
        }
    }

    /**
     * Stop recording and wait for pending frames to finish.
     */
    public void stopEncodingFrames() {
        isRecording = false;

        // Wait for queued frames to be processed (timeout: 30 seconds)
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                Log.w(TAG, "Executor did not terminate in time");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for executor", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Log.i(TAG, "Encoding stopped. Dropped frames: " + droppedFrameCount);
    }

    /**
     * Set recording state.
     */
    public void setRecording(boolean recording) {
        this.isRecording = recording;
    }

    /**
     * Get number of dropped frames.
     */
    public long getDroppedFrameCount() {
        return droppedFrameCount;
    }

    /**
     * Get current queue size (for monitoring).
     */
    public int getQueueSize() {
        return frameQueue.size();
    }

    /**
     * Inner class: Background thread for encoding frames.
     */
    private class FrameEncoderTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // Wait for next frame with timeout
                    Image image = frameQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (image != null) {
                        // Encode and save frame
                        boolean success = frameSaver.saveFrame(image);

                        if (!success) {
                            Log.e(TAG, "Failed to save frame");
                        }
                    } else if (!isRecording && frameQueue.isEmpty()) {
                        // No more frames and not recording
                        break;
                    }

                } catch (InterruptedException e) {
                    Log.d(TAG, "FrameEncoderTask interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in frame encoding", e);
                }
            }

            Log.d(TAG, "FrameEncoderTask finished");
        }
    }
}
