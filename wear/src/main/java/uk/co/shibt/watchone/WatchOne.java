package uk.co.shibt.watchone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.graphics.Color.argb;

public class WatchOne extends CanvasWatchFaceService {

    private static final Typeface MONOTYPE = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(60);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<WatchOne.Engine> mWeakReference;

        public EngineHandler(WatchOne.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchOne.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 8f;
        private static final float MINUTE_STROKE_WIDTH = 4f;

        private static final int SMALL_RADIUS = 3;
        private static final int BIG_RADIUS = 6;
        private static final int SHADOW_RADIUS = 6;
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private final Rect textBounds = new Rect();
        public int level, mWatchHandColor, mWatchHandShadowColor, mTickColor;
        public float lvl, battCircle, sweepAngle, sweepAngleRev, mWidth, mCenterX, mCenterY,
                mHeight, CENTER_GAP_AND_CIRCLE_RADIUS, sMinuteHandLength, sHourHandLength;
        SharedPreferences mSharedPref;
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode, mLowBitAmbient, mBurnInProtection, mAmbient;
        private boolean mRegisteredBattReceiver = false;
        /* Handler to update the time once a second in interactive mode. */
        private Paint mbattPaint, mBackgroundPaint, mDigitalTextPaint, mTextPaints,
                mBatteryText, mBattVoid, mHourPaint, mMinutePaint,
                mTickAndCirclePaint, mFillCirclePaint;
        private final BroadcastReceiver mBattReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                lvl = level / 100f;
                battCircle = mCenterX * lvl;

                if (level > 66) {
                    String bl = String.format("%02x", (100 - level) * 255 / 33);
                    bl = "#80" + bl + "FF00";
                    mbattPaint.setColor(Color.parseColor(bl));
                } else if (level > 16) {
                    String bl = String.format("%02x", (level - 16) * 255 / 50);
                    bl = "#80FF" + bl + "00";
                    mbattPaint.setColor(Color.parseColor(bl));
                } else {
                    String bl = "#80FF0000";
                    mbattPaint.setColor(Color.parseColor(bl));
                }

                sweepAngle = 170 - (170 * lvl);
                sweepAngleRev = (170 * lvl) - 170;
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchOne.this)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mTickColor = Color.LTGRAY;
            mWatchHandColor = argb(255, 255, 0, 0);
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mFillCirclePaint = new Paint();
            mFillCirclePaint.setColor(Color.BLACK);
            mFillCirclePaint.setAntiAlias(true);
            mFillCirclePaint.setStyle(Paint.Style.FILL);
            mFillCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mTickColor);
            mTickAndCirclePaint.setStrokeWidth(2f);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.FILL);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mDigitalTextPaint = new Paint();
            mDigitalTextPaint.setTypeface(MONOTYPE);
            mDigitalTextPaint.setAntiAlias(true);
            mDigitalTextPaint.setTextAlign(Paint.Align.CENTER);
            mDigitalTextPaint.setTextSize(60f);
            mDigitalTextPaint.setColor(argb(255, 255, 255, 255));

            mTextPaints = new Paint();
            mTextPaints.setTypeface(MONOTYPE);
            mTextPaints.setAntiAlias(true);
            mTextPaints.setTextAlign(Paint.Align.CENTER);
            mTextPaints.setColor(Color.WHITE);

            mBatteryText = new Paint();
            mBatteryText.setTypeface(MONOTYPE);
            mBatteryText.setAntiAlias(true);
            mBatteryText.setTextAlign(Paint.Align.CENTER);
            mBatteryText.setColor(argb(255, 255, 255, 255));

            mbattPaint = new Paint();
            mbattPaint.setStyle(Paint.Style.STROKE);
            mbattPaint.setAntiAlias(true);
            mbattPaint.setStrokeWidth(SMALL_RADIUS);

            mBattVoid = new Paint();
            mBattVoid.setAntiAlias(true);
            mBattVoid.setStyle(Paint.Style.STROKE);
            mBattVoid.setStrokeWidth(BIG_RADIUS);
            mBattVoid.setColor(Color.BLACK);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mHourPaint.setColor(mTickColor);
                mMinutePaint.setColor(mTickColor);
                mTickAndCirclePaint.setColor(mTickColor);
                mDigitalTextPaint.setColor(argb(127, 255, 255, 255));
                mBatteryText.setColor(argb(127, 255, 255, 255));

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

                mDigitalTextPaint.setTextSize((float) 60 * 2);

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mTickAndCirclePaint.setColor(mTickColor);
                mDigitalTextPaint.setColor(argb(255, 255, 255, 255));
                mBatteryText.setColor(argb(255, 255, 255, 255));

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                mDigitalTextPaint.setTextSize(60f);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;
            mWidth = width;
            mHeight = height;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            sMinuteHandLength = (float) (mCenterX * 1);
            sHourHandLength = (float) (mCenterX * 1);
            CENTER_GAP_AND_CIRCLE_RADIUS = (float) (mCenterX * 0.9);

        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawBattery(canvas);
            drawWatchFace(canvas);
            drawDigitalText(canvas);

        }

        private void drawBackground(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
        }

        private void drawBattery(Canvas canvas) {
            if (!mAmbient) {
                RectF rectF = new RectF(0 + SMALL_RADIUS, 0 + SMALL_RADIUS,
                        mWidth - SMALL_RADIUS, mHeight - SMALL_RADIUS);
                canvas.drawArc(rectF, 280, 340, false, mbattPaint);
                canvas.drawArc(rectF, 280, sweepAngle, true, mBattVoid);
                canvas.drawArc(rectF, 260, sweepAngleRev, true, mBattVoid);
            }
            canvas.drawText(String.valueOf(level) + "%", mCenterX, 20, mBatteryText);
        }

        private void drawWatchFace(Canvas canvas) {
            if (!mAmbient) {
                float innerTickRadius = mCenterX - 10;
                float outerTickRadius = mCenterX;
                for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                    float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                    float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                    float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                    float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                    float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
                }

                final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
                final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
                final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

                /*
                 * Save the canvas state before we can begin to rotate it.
                 */
                canvas.save();

                canvas.rotate(hoursRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - sHourHandLength,
                        mHourPaint);

                canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - sMinuteHandLength,
                        mMinutePaint);


                // NONE AMBIIENT STUFF HERE
                canvas.restore();
            }

            /* Restore the canvas' original orientation. */
            //canvas.restore();
        }

        private void drawDigitalText(Canvas canvas) {
            int mTextHeight;

            String Hour = String.format("%02d%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE));

            mDigitalTextPaint.getTextBounds(Hour, 0, Hour.length(), textBounds);
            mTextHeight = textBounds.height(); // Use height from getTextBounds()

            canvas.drawText(Hour, mCenterX, mCenterY + (mTextHeight / 2f), mDigitalTextPaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            if (mRegisteredBattReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            IntentFilter bLevel = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            WatchOne.this.registerReceiver(mTimeZoneReceiver, filter);
            WatchOne.this.registerReceiver(mBattReceiver, bLevel);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WatchOne.this.unregisterReceiver(mTimeZoneReceiver);
            WatchOne.this.unregisterReceiver(mBattReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
