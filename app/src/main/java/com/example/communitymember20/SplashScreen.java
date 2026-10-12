package com.example.communitymember20;



import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashScreen extends AppCompatActivity {

    private static final long LOADING_DURATION_MS = 4500; // total fill time
    private static final long TICK_MS = 50;               // update every 50ms

    private ProgressBar loadingProgress;
    private TextView progressText;
    private TextView openingText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startUptime;
    private boolean welcomeStarted = false;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            long elapsed = SystemClock.uptimeMillis() - startUptime;
            int progress = (int) Math.min(100, (elapsed * 100) / LOADING_DURATION_MS);

            loadingProgress.setProgress(progress);
            progressText.setText(progress + "%");

            if (progress < 30)      openingText.setText("Opening Aquacom...");
            else if (progress < 70) openingText.setText("Preparing your water tools...");
            else if (progress < 100) openingText.setText("Almost ready...");
            else                    openingText.setText("Welcome to Aquacom");

            if (progress < 100) {
                handler.postDelayed(this, TICK_MS);
            } else if (!welcomeStarted) {
                welcomeStarted = true;
                startActivity(new Intent(SplashScreen.this, WelcomePage.class));
                finish(); // so Back doesn't return to the splash
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();
        setContentView(R.layout.splash_screen);

        loadingProgress = findViewById(R.id.loadingProgress);
        progressText = findViewById(R.id.progressText);
        openingText = findViewById(R.id.openingText);
        ImageView waterDrop = findViewById(R.id.waterDrop);

        loadingProgress.setMax(100);
        loadingProgress.setProgress(0);
        progressText.setText("0%");
        openingText.setText("Opening Aquacom...");

        startDropAnimation(waterDrop);

        startUptime = SystemClock.uptimeMillis();
        handler.postDelayed(progressTick, TICK_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void startDropAnimation(ImageView drop) {
        drop.setAlpha(0f);
        drop.setScaleX(0.94f);
        drop.setScaleY(0.94f);
        drop.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).start();

        android.animation.ObjectAnimator floating =
                android.animation.ObjectAnimator.ofFloat(drop, View.TRANSLATION_Y, 0f, -8f, 0f);
        floating.setDuration(2600);
        floating.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        floating.setInterpolator(new AccelerateDecelerateInterpolator());
        floating.start();
    }
}
