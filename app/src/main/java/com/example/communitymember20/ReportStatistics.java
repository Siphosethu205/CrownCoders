package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportStatistics extends BaseActivity {

    private static final String TAG = "ReportStatistics";

    // Chart UI
    private TextView tvNoWaterCount, tvLowCount, tvAvailableCount;
    private View barNoWater, barLow, barAvailable;

    // Summary UI
    private TextView tvTotalWaterUpdates, tvTotalEmergency, tvResolvedEmergency, tvPendingEmergency;

    // Firebase
    private DatabaseReference dbWater, dbEmergency;

    // Chart scaling constants
    private final int MAX_BAR_HEIGHT_DP = 140;
    private final int FIXED_MAX_SCALE = 10;

    // ✅ Cached stats so we can build the CSV on demand
    private int statTotalWater = 0;
    private int statNoWater = 0;
    private int statLow = 0;
    private int statAvailable = 0;
    private int statTotalEmergency = 0;
    private int statResolvedEmergency = 0;
    private int statPendingEmergency = 0;

    private String lastCsvContent = "";
    private String lastCsvFileName = "";

    // ✅ File picker for CSV export
    private final ActivityResultLauncher<Intent> exportFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) writeCsvToUri(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.report_statistics);

        // 1. Setup Toolbar with Download action
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ✅ Inflate the "Download Report" menu item
        toolbar.inflateMenu(R.menu.menu_report_statistics);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_download_report) {
                exportReport();
                return true;
            }
            return false;
        });

        // 2. Initialize Views
        tvNoWaterCount = findViewById(R.id.tvNoWaterCount);
        tvLowCount = findViewById(R.id.tvLowCount);
        tvAvailableCount = findViewById(R.id.tvAvailableCount);
        barNoWater = findViewById(R.id.barNoWater);
        barLow = findViewById(R.id.barLow);
        barAvailable = findViewById(R.id.barAvailable);

        tvTotalWaterUpdates = findViewById(R.id.tvTotalWaterUpdates);
        tvTotalEmergency = findViewById(R.id.tvTotalEmergency);
        tvResolvedEmergency = findViewById(R.id.tvResolvedEmergency);
        tvPendingEmergency = findViewById(R.id.tvPendingEmergency);

        // 3. Setup Firebase References
        dbWater = FirebaseDatabase.getInstance().getReference("water_statuses");
        dbEmergency = FirebaseDatabase.getInstance().getReference("emergency_requests");

        // 4. Fetch Data
        fetchWaterStats();
        fetchEmergencyStats();
    }

    private void fetchWaterStats() {
        dbWater.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // 1. Total Water Status Updates
                statTotalWater = (int) snapshot.getChildrenCount();
                tvTotalWaterUpdates.setText(String.valueOf(statTotalWater));

                // 2. Chart Data (No Water, Low, Available)
                statNoWater = 0;
                statLow = 0;
                statAvailable = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    String waterStatus = data.child("waterStatus").getValue(String.class);

                    if (waterStatus != null) {
                        if (waterStatus.equalsIgnoreCase("No Water")) statNoWater++;
                        else if (waterStatus.equalsIgnoreCase("Low")) statLow++;
                        else if (waterStatus.equalsIgnoreCase("Available")) statAvailable++;
                    }
                }

                // Update Chart
                updateBarChart(statNoWater, statLow, statAvailable);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void fetchEmergencyStats() {
        dbEmergency.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                statTotalEmergency = 0;
                statResolvedEmergency = 0;
                statPendingEmergency = 0;

                for (DataSnapshot data : snapshot.getChildren()) {
                    statTotalEmergency++;
                    String status = data.child("status").getValue(String.class);

                    if (status != null) {
                        if (status.equalsIgnoreCase("Resolved")
                                || status.equalsIgnoreCase("Delivered")
                                || status.equalsIgnoreCase("Confirmed")) {
                            statResolvedEmergency++;
                        } else if (status.equalsIgnoreCase("Pending")
                                || status.equalsIgnoreCase("Submitted")
                                || status.equalsIgnoreCase("Received")
                                || status.equalsIgnoreCase("Tanker on the way")
                                || status.equalsIgnoreCase("En Route")
                                || status.equalsIgnoreCase("Arrived")) {
                            statPendingEmergency++;
                        }
                    }
                }

                tvTotalEmergency.setText(String.valueOf(statTotalEmergency));
                tvResolvedEmergency.setText(String.valueOf(statResolvedEmergency));
                tvPendingEmergency.setText(String.valueOf(statPendingEmergency));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    /**
     * Helper method to calculate bar heights based on a FIXED scale.
     */
    private void updateBarChart(int noWater, int low, int available) {

        // Update counts above the bars
        tvNoWaterCount.setText(String.valueOf(noWater));
        tvLowCount.setText(String.valueOf(low));
        tvAvailableCount.setText(String.valueOf(available));

        float density = getResources().getDisplayMetrics().density;
        int maxHeightPx = (int) (MAX_BAR_HEIGHT_DP * density);

        int noWaterHeight = (int) (((float) noWater / FIXED_MAX_SCALE) * maxHeightPx);
        int lowHeight = (int) (((float) low / FIXED_MAX_SCALE) * maxHeightPx);
        int availableHeight = (int) (((float) available / FIXED_MAX_SCALE) * maxHeightPx);

        noWaterHeight = Math.min(noWaterHeight, maxHeightPx);
        lowHeight = Math.min(lowHeight, maxHeightPx);
        availableHeight = Math.min(availableHeight, maxHeightPx);

        int minHeight = (int) (4 * density);
        if (noWaterHeight < minHeight) noWaterHeight = minHeight;
        if (lowHeight < minHeight) lowHeight = minHeight;
        if (availableHeight < minHeight) availableHeight = minHeight;

        setBarHeight(barNoWater, noWaterHeight);
        setBarHeight(barLow, lowHeight);
        setBarHeight(barAvailable, availableHeight);
    }

    private void setBarHeight(View bar, int height) {
        android.view.ViewGroup.LayoutParams params = bar.getLayoutParams();
        params.height = height;
        bar.setLayoutParams(params);
    }

    // ============================================================
    // ✅ DOWNLOAD REPORT AS CSV
    // ============================================================
    private void exportReport() {
        StringBuilder csv = new StringBuilder();

        // ---- Header ----
        String generatedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        csv.append("AquaComm — Reports & Statistics\n");
        csv.append("Generated: ").append(generatedAt).append("\n\n");

        // ---- Section 1: Water Status Distribution ----
        csv.append("Water Status Distribution\n");
        csv.append("Category,Count\n");
        csv.append("No Water,").append(statNoWater).append("\n");
        csv.append("Low,").append(statLow).append("\n");
        csv.append("Available,").append(statAvailable).append("\n\n");

        // ---- Section 2: Summary ----
        csv.append("Summary\n");
        csv.append("Metric,Count\n");
        csv.append("Total water status updates,").append(statTotalWater).append("\n");
        csv.append("Total emergency water requests,").append(statTotalEmergency).append("\n");
        csv.append("Resolved emergency water requests,").append(statResolvedEmergency).append("\n");
        csv.append("Pending emergency water requests,").append(statPendingEmergency).append("\n");

        lastCsvContent = csv.toString();

        String dateStr = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        lastCsvFileName = "AquaComm_Statistics_" + dateStr + ".csv";

        // Launch the file picker so the user can choose where to save
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, lastCsvFileName);
        exportFileLauncher.launch(intent);
    }

    private void writeCsvToUri(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, "Could not open file", Toast.LENGTH_SHORT).show();
                return;
            }
            os.write(lastCsvContent.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            Toast.makeText(this,
                    "✓ Report saved as " + lastCsvFileName,
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e(TAG, "Export failed: " + e.getMessage());
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
