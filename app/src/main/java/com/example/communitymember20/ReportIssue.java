package com.example.communitymember20;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ReportIssue extends BaseActivity {

    private static final String TAG = "ReportIssue";
    private static final int LOCATION_PERMISSION_REQUEST = 501;
    private static final int RECORD_AUDIO_REQUEST = 502;

    // Views
    private AutoCompleteTextView dropdownIssueType;
    private ChipGroup chipGroupUrgency;
    private TextInputEditText etDescription, etLocation;
    private TextInputLayout tilLocation, tilDescription;
    private MaterialButton btnSubmitIssue;
    private LinearLayout rowPhoto;
    private ImageView ivPhotoThumb;
    private TextView tvPhotoLabel, tvPhotoSub;

    // Photo Preview
    private FrameLayout layoutPhotoPreview;
    private ImageView ivPhotoPreview;
    private ImageButton btnDeletePhoto;

    // Voice Control UI
    private LinearLayout voiceControlLayout;
    private TextView tvRecordingStatus;
    private MaterialButton btnPlayVoice, btnPauseVoice, btnReSpeak, btnStopRecording;

    // Firebase
    private DatabaseReference dbRef;
    private DatabaseReference notificationsRef;
    private String uid;
    private String userName = "";
    private String userPhone = "";

    // Location
    private FusedLocationProviderClient fusedClient;
    private double lat = 0, lng = 0;

    // Audio Recording
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private long recordingStartTime;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // Photo
    private Uri selectedPhotoUri = null;
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri;
                    ivPhotoThumb.setImageURI(uri);
                    ivPhotoThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivPhotoThumb.setPadding(0, 0, 0, 0);
                    tvPhotoLabel.setText("Photo selected");
                    tvPhotoSub.setText(uri.getLastPathSegment() != null
                            ? uri.getLastPathSegment() : "Tap to change");

                    // Show the large preview underneath
                    ivPhotoPreview.setImageURI(uri);
                    layoutPhotoPreview.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_issue);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            cleanupAudio();
            finish();
        });

        // Bind Views
        dropdownIssueType = findViewById(R.id.dropdownIssueType);
        chipGroupUrgency = findViewById(R.id.chipGroupUrgency);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        tilLocation = findViewById(R.id.tilLocation);
        tilDescription = findViewById(R.id.tilDescription);
        btnSubmitIssue = findViewById(R.id.btnSubmitIssue);
        rowPhoto = findViewById(R.id.rowPhoto);
        ivPhotoThumb = findViewById(R.id.ivPhotoThumb);
        tvPhotoLabel = findViewById(R.id.tvPhotoLabel);
        tvPhotoSub = findViewById(R.id.tvPhotoSub);
        voiceControlLayout = findViewById(R.id.voiceControlLayout);
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus);
        btnStopRecording = findViewById(R.id.btnStopRecording);
        btnPlayVoice = findViewById(R.id.btnPlayVoice);
        btnPauseVoice = findViewById(R.id.btnPauseVoice);
        btnReSpeak = findViewById(R.id.btnReSpeak);

        // NEW: Photo preview bindings
        layoutPhotoPreview = findViewById(R.id.layoutPhotoPreview);
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview);
        btnDeletePhoto = findViewById(R.id.btnDeletePhoto);

        String[] issueTypes = {
                "🔧 Broken Pipe", "💧 Water Leak", "🛢 Damaged Tank",
                "📉 Low Pressure", "⚠ Dirty Water", "Other"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, issueTypes);
        dropdownIssueType.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();
        notificationsRef = FirebaseDatabase.getInstance().getReference("notifications");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        dbRef.child("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userName = snapshot.child("full_name").getValue(String.class);
                        userPhone = snapshot.child("phone_number").getValue(String.class);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });

        // Handlers
        rowPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        tilLocation.setEndIconOnClickListener(v -> requestGps());
        tilDescription.setEndIconOnClickListener(v -> toggleRecording());
        btnSubmitIssue.setOnClickListener(v -> submitIssue());

        // NEW: Delete photo handler
        btnDeletePhoto.setOnClickListener(v -> {
            selectedPhotoUri = null;
            layoutPhotoPreview.setVisibility(View.GONE);
            ivPhotoPreview.setImageURI(null);
            ivPhotoThumb.setImageResource(android.R.drawable.ic_menu_camera);
            ivPhotoThumb.setScaleType(ImageView.ScaleType.CENTER);
            ivPhotoThumb.setPadding(0, 0, 0, 0);
            tvPhotoLabel.setText("Tap to upload");
            tvPhotoSub.setText("Helps the maintenance team prepare");
            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
        });

        // Voice Control Handlers
        voiceControlLayout.setVisibility(View.GONE);
        btnStopRecording.setOnClickListener(v -> stopRecording());
        btnPlayVoice.setOnClickListener(v -> togglePlayback());
        btnPauseVoice.setOnClickListener(v -> pausePlayback());
        btnReSpeak.setOnClickListener(v -> {
            deleteRecording();
            startRecording();
        });
    }

    // ============================================================
    // VOICE RECORDING LOGIC
    // ============================================================
    private void toggleRecording() {
        if (isRecording) stopRecording();
        else startRecording();
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_REQUEST);
            return;
        }

        try {
            File cacheDir = getExternalCacheDir();
            if (cacheDir == null) cacheDir = getCacheDir();
            audioFilePath = new File(cacheDir,
                    "issue_voice_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();

            voiceControlLayout.setVisibility(View.VISIBLE);
            tvRecordingStatus.setVisibility(View.VISIBLE);
            btnStopRecording.setVisibility(View.VISIBLE);
            btnPlayVoice.setVisibility(View.GONE);
            btnPauseVoice.setVisibility(View.GONE);
            btnReSpeak.setVisibility(View.GONE);

            startTimer();
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            Toast.makeText(this, "Could not start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            cleanupRecorder();
        }
    }

    private void stopRecording() {
        if (mediaRecorder == null || !isRecording) return;

        try {
            mediaRecorder.stop();
        } catch (RuntimeException e) {
            Log.e(TAG, "Stop failed: " + e.getMessage());
            if (audioFilePath != null) new File(audioFilePath).delete();
            audioFilePath = null;
        } finally {
            cleanupRecorder();
        }

        isRecording = false;
        stopTimer();

        tvRecordingStatus.setVisibility(View.GONE);
        btnStopRecording.setVisibility(View.GONE);

        if (audioFilePath != null && new File(audioFilePath).exists()) {
            btnPlayVoice.setVisibility(View.VISIBLE);
            btnPauseVoice.setVisibility(View.VISIBLE);
            btnReSpeak.setVisibility(View.VISIBLE);
            btnPlayVoice.setText("Listen");
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
        } else {
            voiceControlLayout.setVisibility(View.GONE);
            Toast.makeText(this, "Recording failed — try again", Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanupRecorder() {
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception ignored) { }
            mediaRecorder = null;
        }
    }

    private void togglePlayback() {
        if (audioFilePath == null || !new File(audioFilePath).exists()) {
            Toast.makeText(this, "No recording available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isPlaying && mediaPlayer != null) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayVoice.setText("Resume");
            return;
        }

        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlayVoice.setText("Playing...");
            return;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            btnPlayVoice.setText("Playing...");

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                btnPlayVoice.setText("Listen");
                mp.release();
                mediaPlayer = null;
            });
        } catch (Exception e) {
            Log.e(TAG, "Playback failed: " + e.getMessage());
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
            cleanupPlayer();
        }
    }

    private void pausePlayback() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayVoice.setText("Resume");
        }
    }

    private void cleanupPlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) { }
            mediaPlayer = null;
        }
        isPlaying = false;
    }

    private void deleteRecording() {
        cleanupPlayer();
        if (audioFilePath != null) {
            File f = new File(audioFilePath);
            if (f.exists()) f.delete();
            audioFilePath = null;
        }
    }

    private void cleanupAudio() {
        stopTimer();
        if (isRecording) {
            try { mediaRecorder.stop(); } catch (Exception ignored) { }
            isRecording = false;
        }
        cleanupRecorder();
        cleanupPlayer();
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    long elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000;
                    long min = elapsed / 60;
                    long sec = elapsed % 60;
                    tvRecordingStatus.setText(String.format(Locale.getDefault(),
                            "🔴 Recording... %02d:%02d", min, sec));
                    timerHandler.postDelayed(this, 500);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    // ============================================================
    // GPS
    // ============================================================
    private void requestGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }
        fetchGps();
    }

    private void fetchGps() {
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) handleLocation(location);
                        else {
                            fusedClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) handleLocation(lastLoc);
                                else Toast.makeText(this,
                                        "Couldn't get GPS. Type the location instead.",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "GPS failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission missing", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleLocation(android.location.Location loc) {
        lat = loc.getLatitude();
        lng = loc.getLongitude();
        String coords = String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng);
        String current = etLocation.getText() != null
                ? etLocation.getText().toString().trim() : "";
        if (TextUtils.isEmpty(current)) etLocation.setText("GPS: " + coords);
        else etLocation.setText(current + " (GPS: " + coords + ")");
        Toast.makeText(this, "Location captured ✓", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchGps();
        } else if (requestCode == RECORD_AUDIO_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // SUBMIT
    // ============================================================
    private void submitIssue() {
        if (isRecording) stopRecording();
        cleanupPlayer();

        String issueTypeRaw = dropdownIssueType.getText().toString().trim();
        if (TextUtils.isEmpty(issueTypeRaw)) {
            Toast.makeText(this, "Please select an issue type", Toast.LENGTH_SHORT).show();
            return;
        }
        String issueType = issueTypeRaw.replaceAll("[^a-zA-Z ]", "").trim();
        String description = etDescription.getText() != null
                ? etDescription.getText().toString().trim() : "";
        String location = etLocation.getText() != null
                ? etLocation.getText().toString().trim() : "";

        if (TextUtils.isEmpty(description)) {
            etDescription.setError("Please describe the issue");
            etDescription.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Please add a location");
            etLocation.requestFocus();
            return;
        }

        int selectedUrgencyId = chipGroupUrgency.getCheckedChipId();
        if (selectedUrgencyId == View.NO_ID) {
            Toast.makeText(this, "Please select an urgency level", Toast.LENGTH_SHORT).show();
            return;
        }
        Chip uChip = findViewById(selectedUrgencyId);
        final String urgency = uChip.getText().toString();

        InfrastructureIssueModel issue = new InfrastructureIssueModel();
        issue.memberId = uid;
        issue.memberName = userName != null ? userName : "Unknown";
        issue.memberPhone = userPhone != null ? userPhone : "";
        issue.issueType = issueType;
        issue.description = description;
        issue.urgency = urgency;
        issue.location = location;
        issue.latitude = lat;
        issue.longitude = lng;
        issue.photoUrl = selectedPhotoUri != null ? selectedPhotoUri.toString() : "";
        issue.voiceNotePath = audioFilePath != null ? audioFilePath : "";
        issue.status = "Pending";
        issue.timestamp = System.currentTimeMillis();

        String id = dbRef.child("infrastructure_issues").push().getKey();
        if (id == null) {
            Toast.makeText(this, "Could not generate report ID", Toast.LENGTH_SHORT).show();
            return;
        }
        issue.issueId = id;

        btnSubmitIssue.setEnabled(false);
        btnSubmitIssue.setText("Submitting…");

        dbRef.child("infrastructure_issues").child(id).setValue(issue)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✓ Issue saved to DB");
                    sendSubmissionNotification(uid, userName, issueType, location, urgency);
                    writeAuditLog(issueType, location);
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {
                    btnSubmitIssue.setEnabled(true);
                    btnSubmitIssue.setText("Submit Report");
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void sendSubmissionNotification(String targetUid, String targetName,
                                            String issueType, String location, String urgency) {
        if (targetUid == null || targetUid.isEmpty() || "Anonymous".equals(targetUid)) return;

        String notifId = notificationsRef.push().getKey();
        if (notifId == null) return;

        String message = "🔧 Your infrastructure issue report was submitted successfully!\n\n" +
                "Issue: " + issueType + "\n" +
                "Urgency: " + urgency + "\n" +
                "Location: " + location + "\n\n" +
                "The maintenance team will review it shortly.";

        Map<String, Object> notif = new HashMap<>();
        notif.put("id", notifId);
        notif.put("message", message);
        notif.put("targetAudience", "Specific User");
        notif.put("targetUserId", targetUid);
        notif.put("targetUserName", targetName);
        notif.put("timestamp", System.currentTimeMillis());
        notif.put("relatedIssueType", issueType);
        notif.put("relatedLocation", location);

        notificationsRef.child(notifId).setValue(notif);
    }

    private void writeAuditLog(String issueType, String location) {
        String logId = dbRef.child("audit_logs").push().getKey();
        if (logId == null) return;
        Map<String, Object> log = new HashMap<>();
        log.put("action", "INFRASTRUCTURE_ISSUE");
        log.put("details", "Reported " + issueType + " at " + location);
        log.put("performedBy", userName != null ? userName : "Community Member");
        log.put("timestamp", System.currentTimeMillis());
        dbRef.child("audit_logs").child(logId).setValue(log);
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Report Submitted ✓")
                .setMessage("Thank you. Your infrastructure issue has been logged and will be reviewed by the maintenance team within 24 hours.")
                .setPositiveButton("Done", (d, w) -> {
                    d.dismiss();
                    cleanupAudio();
                    finish();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        cleanupAudio();
        super.onDestroy();
    }
}