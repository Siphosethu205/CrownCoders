package com.example.communitymember20;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AssignmentHistory extends BaseActivity {

    private RecyclerView rvAssignments;
    private LinearLayout layoutEmpty; // Bound the empty state container
    private MaterialToolbar toolbar;  // MaterialToolbar matching the new XML
    private AssignmentAdapter adapter;
    private List<TankerAssignmentModel> list;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.assignment_history);

        // Setup Toolbar (custom MaterialToolbar - title handled in XML)
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvAssignments = findViewById(R.id.rvAssignments);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        list = new ArrayList<>();
        adapter = new AssignmentAdapter(list);
        rvAssignments.setLayoutManager(new LinearLayoutManager(this));
        rvAssignments.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference("tanker_assignments");

        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                list.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    TankerAssignmentModel m = data.getValue(TankerAssignmentModel.class);
                    if (m != null) {
                        m.assignmentId = data.getKey();
                        list.add(m);
                    }
                }

                // Sort by most recent first
                Collections.sort(list, (a, b) -> Long.compare(b.assignedAt, a.assignedAt));

                // Toggle empty state container (icon + text together)
                if (list.isEmpty()) {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    rvAssignments.setVisibility(View.GONE);
                } else {
                    layoutEmpty.setVisibility(View.GONE);
                    rvAssignments.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Auto-translated error message
                LocaleHelper.showTranslatedToast(AssignmentHistory.this,
                        "Load failed: " + error.getMessage(), Toast.LENGTH_SHORT);
            }
        });
    }
}