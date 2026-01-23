package com.inout.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.inout.app.databinding.FragmentAdminQrBinding;
import com.inout.app.utils.EncryptionHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Fragment for generating and sharing the Company QR Code.
 */
public class AdminQrFragment extends Fragment {

    private static final String TAG = "AdminQrFragment";
    private FragmentAdminQrBinding binding;
    private Bitmap generatedQrBitmap; // To store the bitmap for sharing

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnGenerateQr.setOnClickListener(v -> generateCompanyQr());
        
        // Share Button Listener
        binding.btnShare_qr.setOnClickListener(v -> {
            if (generatedQrBitmap != null) {
                shareQrImage();
            }
        });
    }

    private void generateCompanyQr() {
        EncryptionHelper encryptionHelper = EncryptionHelper.getInstance(requireContext());

        String configJson = encryptionHelper.getFirebaseConfig();
        String companyName = encryptionHelper.getCompanyName();
        String projectId = encryptionHelper.getProjectId();

        if (configJson == null || projectId == null) {
            Toast.makeText(getContext(), "Error: Config not found.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("firebaseConfig", configJson);
            payload.put("companyName", companyName);
            payload.put("projectId", projectId);
            payload.put("timestamp", System.currentTimeMillis());

            String encryptedPayload = encryptionHelper.encryptQrPayload(payload.toString());

            if (encryptedPayload != null) {
                generatedQrBitmap = encodeAsBitmap(encryptedPayload);
                
                if (generatedQrBitmap != null) {
                    binding.ivQrCode.setImageBitmap(generatedQrBitmap);
                    binding.ivQrCode.setVisibility(View.VISIBLE);
                    binding.tvPlaceholder.setVisibility(View.GONE);
                    binding.btnShare_qr.setVisibility(View.VISIBLE); // Show share button
                    
                    binding.tvInstruction.setText("Company: " + companyName);
                    Toast.makeText(getContext(), "QR Generated Successfully", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "QR Generation failed", e);
            Toast.makeText(getContext(), "Failed to generate QR", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Official Android Share Logic
     */
    private void shareQrImage() {
        try {
            // 1. Create a temporary file in the app's cache
            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs(); // Create folders if they don't exist
            FileOutputStream stream = new FileOutputStream(cachePath + "/company_qr.png");
            
            // 2. Compress the QR bitmap into the file
            generatedQrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            // 3. Get the content URI using FileProvider (Package name must match Manifest)
            File imagePath = new File(requireContext().getCacheDir(), "images");
            File newFile = new File(imagePath, "company_qr.png");
            Uri contentUri = FileProvider.getUriForFile(requireContext(), "com.inout.app.fileprovider", newFile);

            if (contentUri != null) {
                // 4. Create and launch the Share Intent
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Required for receiving apps
                shareIntent.setDataAndType(contentUri, requireContext().getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Company Registration QR");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Scan this QR code to join " + 
                        EncryptionHelper.getInstance(getContext()).getCompanyName());
                
                startActivity(Intent.createChooser(shareIntent, "Share QR via:"));
            }
        } catch (IOException e) {
            Log.e(TAG, "Sharing failed", e);
            Toast.makeText(getContext(), "Could not share image", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap encodeAsBitmap(String content) throws WriterException {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        BitMatrix bitMatrix = multiFormatWriter.encode(content, BarcodeFormat.QR_CODE, 512, 512);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        return barcodeEncoder.createBitmap(bitMatrix);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}