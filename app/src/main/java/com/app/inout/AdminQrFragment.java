package com.inout.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * Fragment responsible for generating the Company QR Code.
 * The QR contains an encrypted JSON payload with Firebase configuration.
 */
public class AdminQrFragment extends Fragment {

    private static final String TAG = "AdminQrFragment";
    private FragmentAdminQrBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnGenerateQr.setOnClickListener(v -> generateCompanyQr());
    }

    /**
     * Retrieves company data, encrypts it, and generates a QR code bitmap.
     */
    private void generateCompanyQr() {
        EncryptionHelper encryptionHelper = EncryptionHelper.getInstance(requireContext());

        // 1. Get Data from Secure Storage
        String configJson = encryptionHelper.getFirebaseConfig();
        String companyName = encryptionHelper.getCompanyName();
        String projectId = encryptionHelper.getProjectId();

        if (configJson == null || projectId == null) {
            Toast.makeText(getContext(), "Error: Configuration not found. Please re-setup.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // 2. Create the Payload Wrapper
            JSONObject payload = new JSONObject();
            payload.put("firebaseConfig", configJson);
            payload.put("companyName", companyName);
            payload.put("projectId", projectId);
            payload.put("timestamp", System.currentTimeMillis());

            String plainTextPayload = payload.toString();

            // 3. Encrypt the entire JSON string
            // This ensures only the Inout app can read the config
            String encryptedPayload = encryptionHelper.encryptQrPayload(plainTextPayload);

            if (encryptedPayload != null) {
                // 4. Generate QR Code Bitmap using ZXing
                Bitmap qrBitmap = encodeAsBitmap(encryptedPayload);
                
                if (qrBitmap != null) {
                    binding.ivQrCode.setImageBitmap(qrBitmap);
                    binding.ivQrCode.setVisibility(View.VISIBLE);
                    binding.tvInstruction.setText("Company: " + companyName + "\nEmployees can scan this to join.");
                    
                    // Show success message
                    Toast.makeText(getContext(), "QR Generated Successfully", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Encryption Error", Toast.LENGTH_SHORT).show();
            }

        } catch (JSONException e) {
            Log.e(TAG, "JSON error generating QR", e);
            Toast.makeText(getContext(), "Failed to create payload", Toast.LENGTH_SHORT).show();
        } catch (WriterException e) {
            Log.e(TAG, "ZXing error generating QR", e);
            Toast.makeText(getContext(), "Failed to generate image", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Helper to convert an encrypted string into a Bitmap QR code.
     */
    private Bitmap encodeAsBitmap(String content) throws WriterException {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        // Set size of QR code (500x500 pixels)
        BitMatrix bitMatrix = multiFormatWriter.encode(content, BarcodeFormat.QR_CODE, 500, 500);
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        return barcodeEncoder.createBitmap(bitMatrix);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}