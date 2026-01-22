package com.inout.app.utils;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for Date and Time formatting and calculations.
 */
public class TimeUtils {

    private static final String TAG = "TimeUtils";

    // Format for Firestore Document IDs (e.g., "2026-01-22")
    private static final SimpleDateFormat DATE_ID_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    // Format for display and storage (e.g., "09:30 AM")
    private static final SimpleDateFormat TIME_DISPLAY_FORMAT = new SimpleDateFormat("hh:mm a", Locale.US);

    // Format for internal parsing/sorting (e.g., "2026-01-22 09:30:00")
    private static final SimpleDateFormat FULL_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    /**
     * @return Current date string (e.g., "2026-01-22") used as the Key in Firestore attendance map.
     */
    public static String getCurrentDateId() {
        return DATE_ID_FORMAT.format(new Date());
    }

    /**
     * @return Current time string for display (e.g., "09:30 AM").
     */
    public static String getCurrentTime() {
        return TIME_DISPLAY_FORMAT.format(new Date());
    }

    /**
     * @return Current full timestamp for sorting.
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Calculates the duration between two time strings (e.g., "09:00 AM" and "05:00 PM").
     *
     * @param checkInTimeStr  The check-in time string.
     * @param checkOutTimeStr The check-out time string.
     * @return A formatted string like "8h 00m" or "N/A" if parsing fails.
     */
    public static String calculateDuration(String checkInTimeStr, String checkOutTimeStr) {
        if (checkInTimeStr == null || checkOutTimeStr == null) return "0h 00m";

        try {
            // We need to parse relative to the same day to get the difference
            // Since the input is just time (HH:mm a), we use a dummy date.
            Date checkIn = TIME_DISPLAY_FORMAT.parse(checkInTimeStr);
            Date checkOut = TIME_DISPLAY_FORMAT.parse(checkOutTimeStr);

            if (checkIn != null && checkOut != null) {
                long diffMillis = checkOut.getTime() - checkIn.getTime();

                // Handle case where checkout is next day (though unlikely in this flow, good for safety)
                if (diffMillis < 0) {
                    diffMillis += TimeUnit.DAYS.toMillis(1);
                }

                long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60;

                return String.format(Locale.US, "%dh %02dm", hours, minutes);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error calculating duration", e);
        }
        return "Error";
    }
    
    public static String formatTimestampToDate(long timestamp) {
        return DATE_ID_FORMAT.format(new Date(timestamp));
    }
}