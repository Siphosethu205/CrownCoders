package com.example.communitymember20;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class EditEmergencyRequestActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private TextInputEditText etLocation, etDate;
    private AutoCompleteTextView actUrgency;
    private MaterialButton btnUpdateRequest;

    private DatabaseReference emergencyRef;
    private String requestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_emergency_request);

        emergencyRef = FirebaseDatabase.getInstance().getReference("emergency_requests");

        toolbar = findViewById(R.id.toolbar);
        etLocation = findViewById(R.id.etLocation);
        etDate = findViewById(R.id.etDate);
        actUrgency = findViewById(R.id.actUrgency);
        btnUpdateRequest = findViewById(R.id.btnUpdateRequest);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup Urgency Dropdown
        String[] urgencyLevels = new String[]{"Standard", "Critical", "Extreme"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, urgencyLevels);
        actUrgency.setAdapter(adapter);

        // Retrieve existing data
        if (getIntent() != null) {
            requestId = getIntent().getStringExtra("REQUEST_ID");
            String location = getIntent().getStringExtra("LOCATION");
            String urgency = getIntent().getStringExtra("URGENCY");
            String date = getIntent().getStringExtra("DATE");

            if (date != null) etDate.setText(date);
            if (location != null) etLocation.setText(location);
            if (urgency != null) actUrgency.setText(urgency, false);
        }

        btnUpdateRequest.setOnClickListener(v -> updateRequest());
    }

    private void updateRequest() {
        String newLocation = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String newUrgency = actUrgency.getText().toString().trim();

        if (TextUtils.isEmpty(newLocation)) {
            Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(newUrgency)) {
            Toast.makeText(this, "Please select an urgency level", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestId == null) {
            Toast.makeText(this, "Error: Request ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUpdateRequest.setEnabled(false);
        btnUpdateRequest.setText("Updating...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("location", newLocation);
        updates.put("urgencyLevel", newUrgency);
        updates.put("lastUpdated", System.currentTimeMillis());

        emergencyRef.child(requestId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditEmergencyRequestActivity.this, "Request updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnUpdateRequest.setEnabled(true);
                    btnUpdateRequest.setText("Update Request");
                    Toast.makeText(EditEmergencyRequestActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}