package com.example.communitymember20;



import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class SplashBackgroundView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SplashBackgroundView(Context context) {
        super(context);
    }

    public SplashBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SplashBackgroundView(
            Context context,
            AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        float height = getHeight();

        /*
         * Positions are percentages of the screen height.
         *
         * 0.00 = top
         * 1.00 = bottom
         */

        float waterStart = height * 0.27f;
        float waterEnd = height * 0.78f;

        LinearGradient gradient = new LinearGradient(
                0,
                0,
                0,
                height,
                new int[]{
                        0xFFFFFFFF,   // Pure white
                        0xFFFFFFFF,   // White
                        0xFFF6FEFE,   // Very soft aqua-white
                        0xFFD9F7F8,   // Pale aqua
                        0xFF48B9C4,   // Waterline aqua
                        0xFF128EA4,   // Teal
                        0xFF075A73,   // Deep teal
                        0xFF04384E    // Deep blue
                },
                new float[]{
                        0.00f,
                        0.18f,
                        0.27f,
                        0.34f,
                        0.42f,
                        0.55f,
                        0.72f,
                        1.00f
                },
                Shader.TileMode.CLAMP
        );

        paint.setShader(gradient);

        canvas.drawRect(
                0,
                0,
                getWidth(),
                height,
                paint
        );
    }
}
