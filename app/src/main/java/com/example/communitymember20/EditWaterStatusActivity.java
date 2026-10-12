package com.example.communitymember20;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class EditWaterStatusActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private TextInputEditText etDate, etLocation;
    private AutoCompleteTextView actWaterLevel;
    private MaterialButton btnSaveChanges;

    private DatabaseReference waterRef;
    private String reportId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_water_status);

        waterRef = FirebaseDatabase.getInstance().getReference("water_statuses");

        toolbar = findViewById(R.id.toolbar);
        etDate = findViewById(R.id.etDate);
        etLocation = findViewById(R.id.etLocation);
        actWaterLevel = findViewById(R.id.actWaterLevel);
        btnSaveChanges = findViewById(R.id.btnSaveChanges);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup Water Level Dropdown
        String[] waterLevels = new String[]{"Available", "Low", "No Water"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, waterLevels);
        actWaterLevel.setAdapter(adapter);

        // Retrieve data from Intent
        if (getIntent() != null) {
            reportId = getIntent().getStringExtra("REPORT_ID");
            String location = getIntent().getStringExtra("LOCATION");
            String waterLevel = getIntent().getStringExtra("WATER_LEVEL");
            String date = getIntent().getStringExtra("DATE");

            if (date != null) etDate.setText(date);
            if (location != null) etLocation.setText(location);
            if (waterLevel != null) actWaterLevel.setText(waterLevel, false);
        }

        btnSaveChanges.setOnClickListener(v -> saveChanges());
    }

    private void saveChanges() {
        String newLocation = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String newWaterLevel = actWaterLevel.getText().toString().trim();

        if (TextUtils.isEmpty(newLocation)) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(newWaterLevel)) {
            Toast.makeText(this, "Please select a water level", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reportId == null) {
            Toast.makeText(this, "Error: Report ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSaveChanges.setEnabled(false);
        btnSaveChanges.setText("Saving...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("location", newLocation);
        updates.put("waterStatus", newWaterLevel);
        updates.put("lastUpdated", System.currentTimeMillis());

        waterRef.child(reportId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditWaterStatusActivity.this, "Report updated successfully!", Toast.LENGTH_SHORT).show();

                    // NEW: Tell the previous screen the operation was successful
                    setResult(RESULT_OK);

                    finish(); // Return to the list
                })
                .addOnFailureListener(e -> {
                    btnSaveChanges.setEnabled(true);
                    btnSaveChanges.setText("Save Changes");
                    Toast.makeText(EditWaterStatusActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}