package com.inout.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.inout.app.models.AttendanceRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Utility to generate and share professional attendance reports.
 * Matches the 10-column table layout exactly.
 */
public class CsvExportHelper {

    private static final String TAG = "CsvExportHelper";

    /**
     * Converts the full month list into a CSV-formatted string and opens the share menu.
     * 
     * @param context   Activity or Fragment context.
     * @param records   The list of 30/31 records (including Absents).
     * @param fileName  Suggested name for the file (e.g., "Josy_Vine_Jan_2026.csv").
     */
    public static void exportAttendanceToCsv(Context context, List<AttendanceRecord> records, String fileName) {
        
        // 1. Create the CSV Header Row (10 Columns)
        StringBuilder csvData = new StringBuilder();
        csvData.append("Date,Day,CheckIn,CheckOut,TotalHours,Location,DistanceMeters,FingerprintVerified,GPSVerified,Status\n");

        // 2. Loop through all records and format rows
        for (AttendanceRecord record : records) {
            String date = record.getDate();
            String day = record.getDayOfWeek();
            String in = (record.getCheckInTime() != null) ? record.getCheckInTime() : "--";
            String out = (record.getCheckOutTime() != null) ? record.getCheckOutTime() : "--";
            String hours = (record.getTotalHours() != null) ? record.getTotalHours() : "0h 00m";
            String location = (record.getLocationName() != null) ? record.getLocationName() : "N/A";
            String distance = (record.getCheckInTime() != null) ? String.valueOf(Math.round(record.getDistanceMeters())) : "--";
            
            // Convert Booleans to professional text proof
            String finger = record.isFingerprintVerified() ? "YES" : "NO";
            String gps = record.isGpsVerified() ? "YES" : "NO";
            
            // Handle Proof logic for status
            String status = record.getStatus();

            // Append row to string (escaped with quotes for names/locations containing commas)
            csvData.append(date).append(",")
                    .append(day).append(",")
                    .append(in).append(",")
                    .append(out).append(",")
                    .append(hours).append(",")
                    .append("\"").append(location).append("\",")
                    .append(distance).append(",")
                    .append(finger).append(",")
                    .append(gps).append(",")
                    .append(status).append("\n");
        }

        // 3. Save to a temporary file for sharing (Zero Billing/No Permanent Storage)
        try {
            File folder = new File(context.getCacheDir(), "reports");
            if (!folder.exists()) folder.mkdirs();
            
            File file = new File(folder, fileName + ".csv");
            FileOutputStream outStream = new FileOutputStream(file);
            outStream.write(csvData.toString().getBytes());
            outStream.close();

            // 4. Share the file via Intent
            shareCsvFile(context, file);

        } catch (IOException e) {
            Log.e(TAG, "CSV Generation failed", e);
            Toast.makeText(context, "Error generating CSV file", Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareCsvFile(Context context, File file) {
        // Use the FileProvider defined in your AndroidManifest
        Uri path = FileProvider.getUriForFile(context, "com.inout.app.fileprovider", file);
        
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Attendance Report Export");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_STREAM, path);
        
        context.startActivity(Intent.createChooser(intent, "Export Report via:"));
    }
}