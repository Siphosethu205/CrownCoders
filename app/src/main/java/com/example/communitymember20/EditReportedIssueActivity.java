package com.example.communitymember20;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class EditReportedIssueActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private AutoCompleteTextView dropdownIssueType;
    private ChipGroup chipGroupUrgency;
    private TextInputEditText etDescription, etLocation;
    private MaterialButton btnUpdateIssue;

    private DatabaseReference issuesRef;
    private String issueId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_reported_issue);

        issuesRef = FirebaseDatabase.getInstance().getReference("infrastructure_issues");

        toolbar = findViewById(R.id.toolbar);
        dropdownIssueType = findViewById(R.id.dropdownIssueType);
        chipGroupUrgency = findViewById(R.id.chipGroupUrgency);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        btnUpdateIssue = findViewById(R.id.btnUpdateIssue);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Setup Issue Type Dropdown
        String[] issueTypes = {
                "🔧 Broken Pipe", "💧 Water Leak", "🛢 Damaged Tank",
                "📉 Low Pressure", "⚠ Dirty Water", "Other"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_dropdown, issueTypes);
        dropdownIssueType.setAdapter(adapter);

        // Load existing data
        if (getIntent() != null) {
            issueId = getIntent().getStringExtra("ISSUE_ID");
            String issueType = getIntent().getStringExtra("ISSUE_TYPE");
            String description = getIntent().getStringExtra("DESCRIPTION");
            String location = getIntent().getStringExtra("LOCATION");
            String urgency = getIntent().getStringExtra("URGENCY");

            if (issueType != null) dropdownIssueType.setText(issueType, false);
            if (description != null) etDescription.setText(description);
            if (location != null) etLocation.setText(location);
            if (urgency != null) {
                int chipId = R.id.chipUrgencyMedium; // default
                if ("Low".equalsIgnoreCase(urgency)) chipId = R.id.chipUrgencyLow;
                else if ("High".equalsIgnoreCase(urgency)) chipId = R.id.chipUrgencyHigh;
                else if ("Critical".equalsIgnoreCase(urgency)) chipId = R.id.chipUrgencyCritical;
                chipGroupUrgency.check(chipId);
            }
        }

        btnUpdateIssue.setOnClickListener(v -> updateIssue());
    }

    private void updateIssue() {
        String issueTypeRaw = dropdownIssueType.getText().toString().trim();
        if (TextUtils.isEmpty(issueTypeRaw)) {
            Toast.makeText(this, "Please select an issue type", Toast.LENGTH_SHORT).show();
            return;
        }
        String issueType = issueTypeRaw.replaceAll("[^a-zA-Z ]", "").trim();

        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String location = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";

        if (TextUtils.isEmpty(description)) {
            Toast.makeText(this, "Please describe the issue", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(location)) {
            Toast.makeText(this, "Please add a location", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedUrgencyId = chipGroupUrgency.getCheckedChipId();
        String urgency = "Medium";
        if (selectedUrgencyId != -1) {
            Chip uChip = findViewById(selectedUrgencyId);
            urgency = uChip.getText().toString();
        }

        if (issueId == null) {
            Toast.makeText(this, "Error: Issue ID not found", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUpdateIssue.setEnabled(false);
        btnUpdateIssue.setText("Updating...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("issueType", issueType);
        updates.put("description", description);
        updates.put("location", location);
        updates.put("urgency", urgency);
        updates.put("lastUpdated", System.currentTimeMillis());

        issuesRef.child(issueId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(EditReportedIssueActivity.this, "Issue updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnUpdateIssue.setEnabled(true);
                    btnUpdateIssue.setText("Update Issue");
                    Toast.makeText(EditReportedIssueActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}