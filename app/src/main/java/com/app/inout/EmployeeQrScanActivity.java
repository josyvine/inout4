package com.inout.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.inout.app.databinding.ActivityEmployeeQrScanBinding;
import com.inout.app.utils.EncryptionHelper;
import com.inout.app.utils.FirebaseManager;

import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class EmployeeQrScanActivity extends AppCompatActivity {

    private static final String TAG = "EmployeeQrScanActivity";
    private static final int PERMISSION_REQUEST_CAMERA = 1001;

    private ActivityEmployeeQrScanBinding binding;
    private ExecutorService cameraExecutor;
    private boolean isProcessing = false; // Flag to prevent multiple scans

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmployeeQrScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera initialization failed.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> processImageProxy(scanner, imageProxy));

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void processImageProxy(BarcodeScanner scanner, ImageProxy imageProxy) {
        if (isProcessing || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String rawValue = barcodes.get(0).getRawValue();
                        if (rawValue != null) {
                            handleScannedQr(rawValue);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Barcode processing failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleScannedQr(String encryptedPayload) {
        // Prevent multiple rapid triggers
        if (isProcessing) return;
        isProcessing = true;

        runOnUiThread(() -> {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.tvStatus.setText("Decrypting configuration...");
        });

        // 1. Decrypt the payload
        String decryptedJson = EncryptionHelper.getInstance(this).decryptQrPayload(encryptedPayload);

        if (decryptedJson == null) {
            resetScan("Invalid QR Code. Decryption failed.");
            return;
        }

        try {
            // 2. Parse the payload Wrapper
            // Expected format: { "firebaseConfig": "{...}", "companyName": "ABC", "projectId": "xyz" }
            JSONObject wrapper = new JSONObject(decryptedJson);
            
            String firebaseConfigStr = wrapper.getString("firebaseConfig");
            String companyName = wrapper.getString("companyName");
            String projectId = wrapper.getString("projectId");

            // 3. Save Configuration locally
            boolean success = FirebaseManager.setConfiguration(this, firebaseConfigStr, companyName, projectId);

            if (success) {
                // 4. Initialize Firebase
                FirebaseManager.initialize(this);
                
                runOnUiThread(() -> {
                    Toast.makeText(EmployeeQrScanActivity.this, "Connected to " + companyName, Toast.LENGTH_LONG).show();
                    // Proceed to Login
                    startActivity(new Intent(EmployeeQrScanActivity.this, LoginActivity.class));
                    finish();
                });
            } else {
                resetScan("Configuration failed. Corrupt data.");
            }

        } catch (Exception e) {
            Log.e(TAG, "JSON Parsing error", e);
            resetScan("Invalid QR Data format.");
        }
    }

    private void resetScan(String errorMsg) {
        runOnUiThread(() -> {
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            binding.progressBar.setVisibility(View.GONE);
            binding.tvStatus.setText("Scan Admin QR Code");
            isProcessing = false; // Allow scanning again
        });
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}