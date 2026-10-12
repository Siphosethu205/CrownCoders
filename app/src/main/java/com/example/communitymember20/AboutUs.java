package com.example.communitymember20;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;

public class AboutUs extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_us);

        // Setup Toolbar (custom MaterialToolbar - title handled in XML)
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Email row — opens the user's mail app
        findViewById(R.id.rowEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@aquacom.co.za"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "AquaComm Feedback");
            try {
                startActivity(intent);
            } catch (Exception e) {
                LocaleHelper.showTranslatedToast(this,
                        "No email app found", Toast.LENGTH_SHORT);
            }
        });

        // Website row — opens browser
        findViewById(R.id.rowWebsite).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.aquacom.co.za"));
            try {
                startActivity(intent);
            } catch (Exception e) {
                LocaleHelper.showTranslatedToast(this,
                        "No browser found", Toast.LENGTH_SHORT);
            }
        });

        // Rate row — opens Play Store (with browser fallback)
        findViewById(R.id.rowRate).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });
    }
}