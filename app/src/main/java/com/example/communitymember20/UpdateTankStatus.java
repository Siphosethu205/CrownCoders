package com.example.communitymember20;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class UpdateTankStatus extends BaseActivity {

    private static final String DB_TANKS = "water_tanks";

    private TextView tvLoadDisplay, tvCapacityDisplay;
    private ProgressBar pbLoadPreview;
    private Slider sliderLoad;
    private TextInputEditText etLoadExact, etSource, etNote;
    private RadioButton rbFilling, rbEnRoute, rbIdle;
    private MaterialButton btnSaveUpdate;

    private DatabaseReference dbRef;
    private String uid, tankerId;
    private int capacity = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.update_tank);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvLoadDisplay = findViewById(R.id.tvLoadDisplay);
        tvCapacityDisplay = findViewById(R.id.tvCapacityDisplay);
        pbLoadPreview = findViewById(R.id.pbLoadPreview);
        sliderLoad = findViewById(R.id.sliderLoad);
        etLoadExact = findViewById(R.id.etLoadExact);
        etSource = findViewById(R.id.etSource);
        etNote = findViewById(R.id.etNote);
        rbFilling = findViewById(R.id.rbFilling);
        rbEnRoute = findViewById(R.id.rbEnRoute);
        rbIdle = findViewById(R.id.rbIdle);
        btnSaveUpdate = findViewById(R.id.btnSaveUpdate);

        dbRef = FirebaseDatabase.getInstance().getReference();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();

        sliderLoad.addOnChangeListener((slider, value, fromUser) -> updatePreview((int) value));

        etLoadExact.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String s = etLoadExact.getText() != null ? etLoadExact.getText().toString() : "";
                if (!s.isEmpty()) {
                    try {
                        int v2 = Integer.parseInt(s);
                        v2 = Math.max(0, Math.min(capacity, v2));
                        sliderLoad.setValue(v2);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        btnSaveUpdate.setOnClickListener(v -> saveUpdate());
        loadCurrentTanker();
    }

    private void loadCurrentTanker() {
        dbRef.child("users").child(uid).child("assigned_tanker")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        tankerId = snapshot.getValue(String.class);
                        if (tankerId == null || tankerId.isEmpty()) {
                            Toast.makeText(UpdateTankStatus.this,
                                    "No tanker assigned to you yet", Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        loadTankerInfo(tankerId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void loadTankerInfo(String tid) {
        dbRef.child(DB_TANKS).child(tid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        TankerModel t = snapshot.getValue(TankerModel.class);
                        if (t == null) return;

                        capacity = t.capacity;
                        tvCapacityDisplay.setText("of " + String.format("%,d", capacity) + " L capacity");

                        sliderLoad.setValueFrom(0);
                        sliderLoad.setValueTo(capacity > 0 ? capacity : 10000);
                        sliderLoad.setStepSize(Math.max(1, capacity / 100));
                        sliderLoad.setValue(t.currentLoad);

                        if (t.currentStatus != null) {
                            if (t.currentStatus.equalsIgnoreCase("Filling")) rbFilling.setChecked(true);
                            else if (t.currentStatus.equalsIgnoreCase("En Route")) rbEnRoute.setChecked(true);
                            else rbIdle.setChecked(true);
                        } else rbIdle.setChecked(true);

                        if (t.sourceName != null) etSource.setText(t.sourceName);
                        updatePreview(t.currentLoad);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    private void updatePreview(int litres) {
        tvLoadDisplay.setText(String.format("%,d L", litres));
        int pct = capacity > 0 ? (int) (((float) litres / capacity) * 100) : 0;
        pbLoadPreview.setProgress(pct);
    }

    private void saveUpdate() {
        int litres = (int) sliderLoad.getValue();
        String status = rbFilling.isChecked() ? "Filling"
                : rbEnRoute.isChecked() ? "En Route"
                  : "Idle";
        String source = etSource.getText() != null ? etSource.getText().toString().trim() : "";
        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

        if (TextUtils.isEmpty(source)) {
            etSource.setError("Please enter the source");
            etSource.requestFocus();
            return;
        }

        long now = System.currentTimeMillis();

        Map<String, Object> tankerUpdates = new HashMap<>();
        tankerUpdates.put("currentLoad", litres);
        tankerUpdates.put("currentStatus", status);
        tankerUpdates.put("sourceName", source);
        tankerUpdates.put("lastUpdated", now);
        tankerUpdates.put("updatedBy", uid);
        if (!note.isEmpty()) tankerUpdates.put("lastNote", note);

        dbRef.child(DB_TANKS).child(tankerId).updateChildren(tankerUpdates)
                .addOnSuccessListener(aVoid -> {
                    dbRef.child(DB_TANKS).child(tankerId).child("currentAssignmentId")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    String assignmentId = snapshot.getValue(String.class);
                                    if (assignmentId == null || assignmentId.isEmpty()) {
                                        Toast.makeText(UpdateTankStatus.this,
                                                "Tank updated ✓", Toast.LENGTH_SHORT).show();
                                        finish();
                                        return;
                                    }

                                    Map<String, Object> assignUpdates = new HashMap<>();
                                    assignUpdates.put("lastUpdateAt", now);
                                    assignUpdates.put("lastUpdateBy", uid);
                                    assignUpdates.put("litresLoaded", litres);
                                    if (!note.isEmpty()) assignUpdates.put("lastNote", note);

                                    if ("Filling".equalsIgnoreCase(status)) {
                                        assignUpdates.put("status", "Assigned");
                                    } else if ("En Route".equalsIgnoreCase(status)) {
                                        assignUpdates.put("status", "En Route");
                                    }

                                    dbRef.child("tanker_assignments").child(assignmentId)
                                            .updateChildren(assignUpdates)
                                            .addOnSuccessListener(aVoid2 -> {
                                                Toast.makeText(UpdateTankStatus.this,
                                                        "Tank + assignment updated ✓",
                                                        Toast.LENGTH_SHORT).show();
                                                finish();
                                            });
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) { }
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}