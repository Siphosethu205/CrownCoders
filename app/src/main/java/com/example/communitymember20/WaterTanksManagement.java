package com.example.communitymember20;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaterTanksManagement extends BaseActivity {

    private static final String DB_TANKS = "water_tanks";

    public static final String EXTRA_PICK_MODE = "pick_mode";
    public static final String RESULT_TANKER_ID = "result_tanker_id";
    public static final String RESULT_TANKER_REG = "result_tanker_reg";

    private RecyclerView rvTanks;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyState;
    private ExtendedFloatingActionButton fabAddTank;
    private WaterTankAdapter adapter;
    private List<TankerModel> tankList;
    private DatabaseReference dbRef;
    private boolean isPickMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.water_tanks_management);

        isPickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setTitle(isPickMode ? "Select a Tanker" : "Water Tanks Management");
        toolbar.setTitleTextColor(0xFF0F172A);
        toolbar.setNavigationOnClickListener(v -> {
            if (isPickMode) setResult(RESULT_CANCELED);
            finish();
        });

        rvTanks = findViewById(R.id.rvTanks);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        fabAddTank = findViewById(R.id.fabAddTank);

        tankList = new ArrayList<>();
        adapter = new WaterTankAdapter(tankList, new WaterTankAdapter.OnTankAction() {
            @Override
            public void onEdit(TankerModel t) {
                if (isPickMode) {
                    handlePickModeSelection(t);
                } else {
                    showAddEditDialog(t);
                }
            }

            @Override
            public void onAssignDriver(TankerModel t) {
                if (!isPickMode) showAssignDriverDialog(t);
            }

            @Override
            public void onDelete(TankerModel t) {
                if (!isPickMode) confirmDelete(t);
            }
        }, isPickMode);
        rvTanks.setLayoutManager(new LinearLayoutManager(this));
        rvTanks.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();

        // ✅ FAB always visible so admin can add a tank even in picker mode
        fabAddTank.setOnClickListener(v -> showAddEditDialog(null));

        loadTanks();
    }

    // ============================================================
    // PICK MODE: Handle the tapped tanker
    // ============================================================
    private void handlePickModeSelection(TankerModel tanker) {
        String assignUid = getIntent().getStringExtra("assign_driver_uid");
        String assignName = getIntent().getStringExtra("assign_driver_name");

        if (assignUid != null && !assignUid.isEmpty()) {
            // Launched from Driver Management → assign directly to this driver
            Map<String, Object> updates = new HashMap<>();
            updates.put("assignedDriverId", assignUid);
            updates.put("assignedDriverName", assignName);

            dbRef.child(DB_TANKS).child(tanker.tankerId).updateChildren(updates)
                    .addOnSuccessListener(a -> {
                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                .child(assignUid)
                                .child("assigned_tanker")
                                .setValue(tanker.tankerId);

                        Toast.makeText(WaterTanksManagement.this,
                                "Assigned to " + (assignName != null ? assignName : "driver") + " ✓",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(WaterTanksManagement.this,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
        } else {
            // Launched from AssignTanker → return the picked tanker
            Intent result = new Intent();
            result.putExtra(RESULT_TANKER_ID, tanker.tankerId);
            result.putExtra(RESULT_TANKER_REG, tanker.registration);
            setResult(RESULT_OK, result);
            finish();
        }
    }

    // ============================================================
    // LOAD TANKS
    // ============================================================
    private void loadTanks() {
        dbRef.child(DB_TANKS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tankList.clear();
                for (DataSnapshot t : snapshot.getChildren()) {
                    TankerModel model = t.getValue(TankerModel.class);
                    if (model != null) {
                        model.tankerId = t.getKey();

                        if (isPickMode) {
                            // In pick mode, only show Idle / Available tankers
                            String status = model.currentStatus;
                            if (status == null
                                    || "Idle".equalsIgnoreCase(status)
                                    || "Available".equalsIgnoreCase(status)) {
                                tankList.add(model);
                            }
                        } else {
                            tankList.add(model);
                        }
                    }
                }

                if (tankList.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvTanks.setVisibility(View.GONE);

                    if (tvEmptyState != null) {
                        if (isPickMode) {
                            tvEmptyState.setText("No idle tankers available. Tap + to add a new tank.");
                        } else {
                            tvEmptyState.setText("Tap the + button below to add your first water tank to the fleet.");
                        }
                    }
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvTanks.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(WaterTanksManagement.this,
                        "Load failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ============================================================
    // ADD / EDIT DIALOG
    // ============================================================
    private void showAddEditDialog(TankerModel existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_tank, null);
        TextInputEditText etReg = view.findViewById(R.id.etReg);
        TextInputEditText etCapacity = view.findViewById(R.id.etCapacity);
        TextInputEditText etSource = view.findViewById(R.id.etSource);

        boolean isEdit = existing != null;
        if (isEdit) {
            etReg.setText(existing.registration);
            etCapacity.setText(String.valueOf(existing.capacity));
            etSource.setText(existing.sourceName);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Water Tank" : "Add New Water Tank")
                .setView(view)
                .setPositiveButton(isEdit ? "Save" : "Add", null)
                .setNegativeButton("Cancel", null);

        AlertDialog dialog = b.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reg = etReg.getText() != null ? etReg.getText().toString().trim() : "";
            String capStr = etCapacity.getText() != null ? etCapacity.getText().toString().trim() : "";
            String src = etSource.getText() != null ? etSource.getText().toString().trim() : "";

            if (TextUtils.isEmpty(reg)) { etReg.setError("Required"); return; }
            if (TextUtils.isEmpty(capStr)) { etCapacity.setError("Required"); return; }

            int capacity;
            try {
                capacity = Integer.parseInt(capStr);
            } catch (NumberFormatException e) {
                etCapacity.setError("Invalid number");
                return;
            }

            if (isEdit) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("registration", reg);
                updates.put("capacity", capacity);
                if (!TextUtils.isEmpty(src)) updates.put("sourceName", src);

                dbRef.child(DB_TANKS).child(existing.tankerId).updateChildren(updates)
                        .addOnSuccessListener(a -> {
                            Toast.makeText(this, "Water tank updated ✓", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } else {
                String id = dbRef.child(DB_TANKS).push().getKey();
                if (id == null) return;

                TankerModel tank = new TankerModel();
                tank.tankerId = id;
                tank.registration = reg;
                tank.capacity = capacity;
                tank.currentLoad = 0;
                tank.currentStatus = "Idle";
                tank.sourceName = !TextUtils.isEmpty(src) ? src : "—";
                tank.lastUpdated = System.currentTimeMillis();
                tank.active = true;

                dbRef.child(DB_TANKS).child(id).setValue(tank)
                        .addOnSuccessListener(a -> {
                            Toast.makeText(this, "Water tank added ✓", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ============================================================
    // ASSIGN DRIVER TO TANKER (from Water Tanks Management)
    // ============================================================
    private void showAssignDriverDialog(TankerModel tanker) {
        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> names = new ArrayList<>();
                        List<String> ids = new ArrayList<>();
                        names.add("— Unassign —");
                        ids.add(null);

                        for (DataSnapshot u : snapshot.getChildren()) {
                            String name = u.child("full_name").getValue(String.class);
                            if (name != null) {
                                names.add(name);
                                ids.add(u.getKey());
                            }
                        }

                        if (names.size() == 1) {
                            Toast.makeText(WaterTanksManagement.this,
                                    "No drivers available", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        new AlertDialog.Builder(WaterTanksManagement.this)
                                .setTitle("Assign Driver to " + tanker.registration)
                                .setItems(names.toArray(new String[0]), (d, which) -> {
                                    String selId = ids.get(which);
                                    String selName = names.get(which);

                                    Map<String, Object> updates = new HashMap<>();
                                    if (selId == null) {
                                        updates.put("assignedDriverId", null);
                                        updates.put("assignedDriverName", null);
                                    } else {
                                        updates.put("assignedDriverId", selId);
                                        updates.put("assignedDriverName", selName);
                                    }

                                    dbRef.child(DB_TANKS).child(tanker.tankerId).updateChildren(updates)
                                            .addOnSuccessListener(a -> {
                                                if (selId != null) {
                                                    dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                            .child(selId)
                                                            .child("assigned_tanker")
                                                            .setValue(tanker.tankerId);
                                                }
                                                // Unassign the previous driver if there was one
                                                if (tanker.assignedDriverId != null
                                                        && !tanker.assignedDriverId.equals(selId)) {
                                                    dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                                            .child(tanker.assignedDriverId)
                                                            .child("assigned_tanker")
                                                            .removeValue();
                                                }
                                                Toast.makeText(WaterTanksManagement.this,
                                                        selId == null ? "Unassigned ✓" : "Driver assigned ✓",
                                                        Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e ->
                                                    Toast.makeText(WaterTanksManagement.this,
                                                            "Failed: " + e.getMessage(),
                                                            Toast.LENGTH_LONG).show());
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // ============================================================
    // DELETE TANKER
    // ============================================================
    private void confirmDelete(TankerModel t) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Water Tank")
                .setMessage("Remove " + t.registration + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    // Unassign any driver attached to this tanker first
                    if (t.assignedDriverId != null && !t.assignedDriverId.isEmpty()) {
                        dbRef.child("users").child(UserNodeHelper.NODE_DRIVERS)
                                .child(t.assignedDriverId)
                                .child("assigned_tanker")
                                .removeValue();
                    }
                    dbRef.child(DB_TANKS).child(t.tankerId).removeValue()
                            .addOnSuccessListener(a ->
                                    Toast.makeText(this, "Deleted ✓", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}