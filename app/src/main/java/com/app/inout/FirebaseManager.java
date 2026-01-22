package com.inout.app.utils;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Manages the dynamic initialization of the Firebase backend.
 * This allows the app to connect to different Firebase projects based on the
 * configuration uploaded by the Admin or scanned by the Employee.
 */
public class FirebaseManager {

    private static final String TAG = "FirebaseManager";

    /**
     * Initializes Firebase using the configuration stored in EncryptionHelper.
     * This is called automatically by InOutApplication.
     */
    public static void initialize(Context context) {
        String jsonConfig = EncryptionHelper.getInstance(context).getFirebaseConfig();

        if (jsonConfig != null && !jsonConfig.isEmpty()) {
            try {
                // If a config exists locally, use it to initialize Firebase
                FirebaseOptions options = buildOptionsFromJson(jsonConfig);
                
                // Check if the default app is already initialized
                if (!FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp app = FirebaseApp.getInstance();
                    // If the project ID has changed, we might need to re-init (rare in this flow, usually requires restart)
                    // For now, we assume the app process starts fresh or we just log it.
                    if (!app.getOptions().getApplicationId().equals(options.getApplicationId())) {
                         Log.w(TAG, "Firebase already initialized with different config. Creating named app not supported in this simple flow yet.");
                    }
                } else {
                    FirebaseApp.initializeApp(context, options);
                    Log.d(TAG, "Firebase initialized successfully with DYNAMIC config.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse saved Firebase config.", e);
            }
        } else {
            Log.d(TAG, "No dynamic Firebase config found. Waiting for setup.");
        }
    }

    /**
     * Forces re-initialization of Firebase with a new JSON string.
     * Used when Admin switches companies or Employee scans a new QR.
     * Note: Changing the DEFAULT app usually requires an app restart or using named apps.
     * For this implementation, we will try to re-init if list is empty, otherwise we rely on restart prompt.
     */
    public static boolean setConfiguration(Context context, String jsonConfig, String companyName, String projectId) {
        try {
            // Validate JSON by trying to build options
            buildOptionsFromJson(jsonConfig);

            // Save to encrypted storage
            EncryptionHelper.getInstance(context).saveFirebaseConfig(jsonConfig, companyName, projectId);
            
            Log.d(TAG, "New Firebase configuration saved successfully.");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Invalid Firebase JSON provided.", e);
            return false;
        }
    }

    /**
     * Parses the google-services.json content string and builds FirebaseOptions.
     */
    private static FirebaseOptions buildOptionsFromJson(String jsonString) throws Exception {
        JSONObject root = new JSONObject(jsonString);
        
        // Extract project info
        JSONObject projectInfo = root.getJSONObject("project_info");
        String projectId = projectInfo.getString("project_id");
        String storageBucket = projectInfo.getString("storage_bucket");

        // Extract client info (usually the first client in the array is the Android one)
        JSONArray clientArray = root.getJSONArray("client");
        JSONObject client = clientArray.getJSONObject(0);
        JSONObject clientInfo = client.getJSONObject("client_info");
        String applicationId = clientInfo.getString("mobilesdk_app_id");

        // Extract API Key
        JSONArray apiKeyArray = client.getJSONArray("api_key");
        JSONObject apiKeyObject = apiKeyArray.getJSONObject(0);
        String apiKey = apiKeyObject.getString("current_key");

        return new FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(applicationId)
                .setProjectId(projectId)
                .setStorageBucket(storageBucket)
                .build();
    }
}