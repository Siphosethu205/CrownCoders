package com.example.communitymember20;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyTranslations();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyTranslations();
    }

    private void applyTranslations() {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            LocaleHelper.translateAllViews(this, rootView);
        }
    }
}