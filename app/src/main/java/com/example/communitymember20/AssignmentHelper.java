package com.example.communitymember20;

import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class AssignmentHelper {

    private static final String TAG = "AssignmentHelper";

    private static final String EMAILJS_SERVICE_ID = "service_j8ke2u4";
    private static final String EMAILJS_TEMPLATE_ID = "template_t7pzk1j";
    private static final String EMAILJS_PUBLIC_KEY = "Zxw3AfQDypb2xP6nd";

    public interface Callback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void assign(String emergencyId,
                              String emergencyTitle,
                              String emergencyLocation,
                              String emergencyUrgency,
                              String tankerId,
                              String tankerReg,
                              String driverId,
                              String driverName,
                              String driverEmail,
                              Callback callback) {

        DatabaseReference db = FirebaseDatabase.getInstance().getReference();
        Map<String, Object> updates = new HashMap<>();

        long now = System.currentTimeMillis();

        // Generate OTP for delivery confirmation
        String otp = generateOtp();
        Log.d(TAG, "Generated OTP for assignment: " + otp);

        // Generate assignment ID FIRST so we can reference it
        String assignmentId = db.child("tanker_assignments").push().getKey();
        if (assignmentId == null) {
            callback.onFailure("Could not generate assignment ID");
            return;
        }

        // 1. Emergency request updates — INCLUDING assignedAssignmentId
        updates.put("emergency_requests/" + emergencyId + "/assignedDriverId", driverId);
        updates.put("emergency_requests/" + emergencyId + "/assignedDriverName", driverName);
        updates.put("emergency_requests/" + emergencyId + "/assignedTankerId", tankerId);
        updates.put("emergency_requests/" + emergencyId + "/assignedTankerReg", tankerReg);
        updates.put("emergency_requests/" + emergencyId + "/assignedAssignmentId", assignmentId);
        updates.put("emergency_requests/" + emergencyId + "/status", "Tanker on the way");
        updates.put("emergency_requests/" + emergencyId + "/assignedAt", now);

        // 2. Driver updates
        updates.put("users/drivers/" + driverId + "/assigned_tanker", tankerId);
        updates.put("users/drivers/" + driverId + "/active_emergency_id", emergencyId);
        updates.put("users/drivers/" + driverId + "/active_emergency_location", emergencyLocation);
        updates.put("users/drivers/" + driverId + "/driver_status", "Not Available");

        // 3. Tanker updates
        updates.put("water_tanks/" + tankerId + "/assignedDriverId", driverId);
        updates.put("water_tanks/" + tankerId + "/assignedDriverName", driverName);
        updates.put("water_tanks/" + tankerId + "/currentStatus", "In Use");
        updates.put("water_tanks/" + tankerId + "/currentEmergencyId", emergencyId);
        updates.put("water_tanks/" + tankerId + "/currentAssignmentId", assignmentId);

        // 4. Assignment record
        Map<String, Object> a = new HashMap<>();
        a.put("assignmentId", assignmentId);
        a.put("emergencyRequestId", emergencyId);
        a.put("emergencyRequestTitle", emergencyTitle != null ? emergencyTitle : "Emergency Request");
        a.put("emergencyRequestLocation", emergencyLocation);
        a.put("emergencyRequestUrgency", emergencyUrgency);
        a.put("tankerId", tankerId);
        a.put("tankerReg", tankerReg);
        a.put("driverId", driverId);
        a.put("driverName", driverName);
        a.put("status", "Assigned");
        a.put("assignedAt", now);
        a.put("lastUpdateAt", now);
        a.put("lastNote", "Assigned by admin");
        a.put("deliveryOtp", otp);
        a.put("otpVerified", false);
        a.put("otpVerifiedAt", 0);
        updates.put("tanker_assignments/" + assignmentId, a);

        db.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Assignment saved");
                    createDriverNotification(db, driverId, driverName,
                            emergencyLocation, tankerReg, emergencyUrgency);
                    sendAssignmentEmail(driverEmail, driverName,
                            emergencyLocation, tankerReg, emergencyUrgency);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "✗ Assignment failed: " + e.getMessage());
                    callback.onFailure(e.getMessage());
                });
    }

    private static String generateOtp() {
        SecureRandom random = new SecureRandom();
        int num = 100000 + random.nextInt(900000);
        return String.valueOf(num);
    }

    private static void createDriverNotification(DatabaseReference db,
                                                 String driverId,
                                                 String driverName,
                                                 String location,
                                                 String tankerReg,
                                                 String urgency) {
        String notifId = db.child("notifications").push().getKey();
        if (notifId == null) return;

        String msg = "🚚 You have a new emergency assignment!\n\n" +
                "📍 Location: " + location + "\n" +
                "⚠ Urgency: " + urgency + "\n" +
                "🚛 Tanker: " + tankerReg + "\n\n" +
                "Open your dashboard to see the delivery code.";

        Map<String, Object> n = new HashMap<>();
        n.put("id", notifId);
        n.put("message", msg);
        n.put("targetAudience", "Specific User");
        n.put("targetUserId", driverId);
        n.put("targetUserName", driverName);
        n.put("timestamp", System.currentTimeMillis());
        n.put("type", "EMERGENCY_ASSIGNMENT");
        n.put("isRead", false);

        db.child("notifications").child(notifId).setValue(n);
    }

    private static void sendAssignmentEmail(String email, String name,
                                            String location, String tankerReg,
                                            String urgency) {
        if (email == null || email.isEmpty()) return;

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... v) {
                try {
                    URL url = new URL("https://api.emailjs.com/api/v1.0/email/send");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("origin", "http://localhost");
                    conn.setDoOutput(true);

                    JSONObject tp = new JSONObject();
                    tp.put("to_email", email);
                    tp.put("to_name", name);
                    tp.put("temp_password",
                            "EMERGENCY ASSIGNMENT\n\n" +
                                    "Location: " + location + "\n" +
                                    "Urgency: " + urgency + "\n" +
                                    "Tanker: " + tankerReg + "\n\n" +
                                    "Open the AquaComm app to see your delivery code.");

                    JSONObject p = new JSONObject();
                    p.put("service_id", EMAILJS_SERVICE_ID);
                    p.put("template_id", EMAILJS_TEMPLATE_ID);
                    p.put("user_id", EMAILJS_PUBLIC_KEY);
                    p.put("template_params", tp);

                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(p.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    conn.getResponseCode();
                } catch (Exception e) {
                    Log.e(TAG, "Email failed: " + e.getMessage());
                }
                return null;
            }
        }.execute();
    }
}
