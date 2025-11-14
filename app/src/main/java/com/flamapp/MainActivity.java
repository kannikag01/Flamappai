package com.flamapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Environment;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 100;

    private TextureView cameraView;
    private TextView debugText;

    private HandlerThread cameraThread;
    private Handler cameraHandler;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraSession;
    private Size previewSize;

    // --- Added for frame capture + processing ---
    private ImageReader imageReader;
    private long frameCount = 0;
    private long lastFpsTime = 0;
    private float currentFps = 0f;

    // --- Save-on-next-frame flag ---
    private volatile boolean saveNextProcessedFrame = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.camera_view);
        debugText = findViewById(R.id.debug_text);

        // Long-press to save next processed frame
        cameraView.setOnLongClickListener(view -> {
            saveNextProcessedFrame = true;
            Toast.makeText(this, "Will save next processed frame", Toast.LENGTH_SHORT).show();
            return true;
        });

        log("Initializing…");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST);

        } else {
            startCameraThread();
            setupTextureListener();
        }
    }

    private void log(String msg) {
        runOnUiThread(() -> debugText.setText(msg));
        System.out.println("APP_DEBUG: " + msg);
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private void setupTextureListener() {
        cameraView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                log("Surface ready");
                openCamera();
            }

            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return false; }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture s) {
                // DO NOT PROCESS BITMAPS HERE → IT CAUSES CRASHES
                // Leave empty
            }
        });
    }

    private void openCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                log("Config map null");
                return;
            }

            // Choose a preview size (we take the first available; for speed choose a smaller one if needed)
            previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            // --- Initialize ImageReader to capture frames in YUV format ---
            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                     ImageFormat.YUV_420_888, 2);

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) {
                        System.out.println("APP_DEBUG: image == null");
                        return;
                    }

                    // Extract Y plane (luminance) into contiguous byte[]
                    Image.Plane yPlane = image.getPlanes()[0];
                    ByteBuffer buffer = yPlane.getBuffer();
                    int rowStride = yPlane.getRowStride();
                    int pixelStride = yPlane.getPixelStride();

                    int width = previewSize.getWidth();
                    int height = previewSize.getHeight();
                    byte[] yBytes = new byte[width * height];

                    if (rowStride == width && pixelStride == 1) {
                        // fast path: contiguous
                        buffer.get(yBytes, 0, yBytes.length);
                    } else {
                        // safe path: copy row by row
                        byte[] row = new byte[rowStride];
                        for (int r = 0; r < height; ++r) {
                            buffer.get(row, 0, rowStride);
                            System.arraycopy(row, 0, yBytes, r * width, width);
                        }
                    }

                    // Try native processing
                    byte[] processed = null;
                    try {
                        processed = FrameProcessor.processGrayFrame(yBytes, width, height);
                        System.out.println("APP_DEBUG: native processed returned " + (processed == null ? "null" : processed.length + " bytes"));
                    } catch (Throwable t) {
                        t.printStackTrace();
                        System.out.println("APP_DEBUG: native processing threw: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                    }

                    // Helper to convert grayscale byte[] -> Bitmap
                    java.util.function.Function<byte[], Bitmap> toBitmap = (bytes) -> {
                        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        int[] pixels = new int[bytes.length];
                        for (int i = 0; i < bytes.length; i++) {
                            int v = bytes[i] & 0xFF;
                            pixels[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
                        }
                        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
                        return bmp;
                    };

                    // If native returned bytes, use them; otherwise we will optionally save raw Y plane
                    Bitmap bmpToRender = null;
                    if (processed != null && processed.length == width * height) {
                        bmpToRender = toBitmap.apply(processed);
                    } else {
                        // If native failed, still convert raw Y-plane for display/debug/save
                        bmpToRender = toBitmap.apply(yBytes);
                    }

                    // If requested, save this processed bitmap (once)
                    if (saveNextProcessedFrame) {
                        saveNextProcessedFrame = false;
                        // If processed was null we will still save the Y-plane version (fallback)
                        saveBitmapToFile(bmpToRender);
                        System.out.println("APP_DEBUG: saveNextProcessedFrame triggered, saved fallback bitmap");
                    }

                    // Render to TextureView
                    final Bitmap finalBmp = bmpToRender;
                    runOnUiThread(() -> {
                        if (cameraView.isAvailable()) {
                            Canvas canvas = null;
                            try {
                                canvas = cameraView.lockCanvas();
                                if (canvas != null) {
                                    Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                                    canvas.drawBitmap(finalBmp, null, dest, null);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            } finally {
                                if (canvas != null) cameraView.unlockCanvasAndPost(canvas);
                            }
                        }
                    });

                    // FPS counting
                    frameCount++;
                    long now = SystemClock.elapsedRealtime();
                    if (lastFpsTime == 0) lastFpsTime = now;
                    if (now - lastFpsTime >= 1000) {
                        currentFps = (frameCount * 1000f) / (now - lastFpsTime);
                        frameCount = 0;
                        lastFpsTime = now;
                        final float f = currentFps;
                        runOnUiThread(() -> debugText.setText(String.format("FPS: %.1f", f)));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("APP_DEBUG: listener exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    if (image != null) image.close();
                }
            }, cameraHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) return;

            log("Opening camera…");

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    log("Camera opened");
                    startPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    log("Camera disconnected");
                    camera.close();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    log("Camera error");
                    camera.close();
                }
            }, cameraHandler);

        } catch (Exception e) {
            e.printStackTrace();
            log("Camera open failed");
        }
    }

    private void startPreview() {
        try {
            if (cameraDevice == null) {
                log("No camera device");
                return;
            }

            if (!cameraView.isAvailable()) {
                log("Texture not available");
                return;
            }

            SurfaceTexture surfaceTexture = cameraView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            CaptureRequest.Builder builder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            builder.addTarget(previewSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            log("Starting preview…");

            // include both preview and imageReader surfaces
            Surface imageSurface = (imageReader != null) ? imageReader.getSurface() : null;

            if (imageSurface != null) {
                cameraDevice.createCaptureSession(
                        Arrays.asList(previewSurface, imageSurface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                cameraSession = session;

                                try {
                                    // add image reader target so camera sends frames to it
                                    builder.addTarget(imageSurface);
                                    session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                    log("Preview running");
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    log("Preview error");
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                log("Session failed");
                            }
                        },
                        cameraHandler
                );
            } else {
                // fallback: only preview surface
                cameraDevice.createCaptureSession(
                        Arrays.asList(previewSurface),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                cameraSession = session;
                                try {
                                    session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                    log("Preview running (no imageReader)");
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                    log("Preview error");
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                log("Session failed");
                            }
                        },
                        cameraHandler
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            log("Preview crash");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        stopCameraThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraThread();
        if (cameraView.isAvailable()) {
            openCamera();
        } else {
            setupTextureListener();
        }
    }

    private void closeCamera() {
        try {
            if (cameraSession != null) {
                cameraSession.close();
                cameraSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception ignored) {}
    }

    private void stopCameraThread() {
        try {
            if (cameraThread != null) {
                cameraThread.quitSafely();
                cameraThread.join();
                cameraThread = null;
                cameraHandler = null;
            }
        } catch (Exception ignored) {}
    }

    // -------------------------
    // Helper: save bitmap to file
    // -------------------------
    // -------------------------
// Helper: save bitmap to MediaStore Pictures/Flamapp (visible in Gallery)
// -------------------------
private void saveBitmapToFile(Bitmap bmp) {
    new Thread(() -> {
        try {
            String fname = "processed_" + System.currentTimeMillis() + ".png";

            // Prepare values for MediaStore insert
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fname);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

            // For Android Q+ we can use RELATIVE_PATH so it appears under Pictures/Flamapp
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Flamapp");
            } else {
                // For older devices use the public Pictures directory (requires WRITE_EXTERNAL_STORAGE)
                File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File folder = new File(pictures, "Flamapp");
                if (!folder.exists()) folder.mkdirs();
                File outFile = new File(folder, fname);
                try (OutputStream os = new FileOutputStream(outFile)) {
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) throw new Exception("compress false");
                }
                final String msg = "Saved: " + outFile.getAbsolutePath();
                System.out.println("APP_DEBUG: " + msg);
                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    debugText.setText(msg);
                });
                return;
            }

            // Insert into MediaStore and write
            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("MediaStore insert returned null");

            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) throw new Exception("openOutputStream returned null");
                boolean ok = bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.flush();
                if (!ok) throw new Exception("Bitmap.compress returned false");
            }

            final String msg = "Saved to Gallery: " + uri.toString();
            System.out.println("APP_DEBUG: " + msg);
            runOnUiThread(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                debugText.setText(msg);
            });

        } catch (Exception e) {
            e.printStackTrace();
            final String err = "Save failed: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            System.out.println("APP_DEBUG: " + err);
            runOnUiThread(() -> {
                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                debugText.setText(err);
            });
        }
    }).start();
 }

}
