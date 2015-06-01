/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mortenjust.trajectoryclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import static java.util.Calendar.MINUTE;
import java.util.Date;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private static final float HAND_END_CAP_RADIUS = 4f;
        private static final float SHADOW_RADIUS = 6f;


        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;

        private static final float STROKE_WIDTH = 2f;

        private Time mTime;

        private Paint mBackgroundPaint;
        private Paint mHandPaint;
        private Paint trajectoryHandPaint;

        private boolean mAmbient;

        private Bitmap mBackgroundBitmap;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            final int backgroundResId = R.drawable.custom_background;



            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), backgroundResId);

            mHandPaint = new Paint();
            mHandPaint.setColor(Color.WHITE);
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);

            trajectoryHandPaint = new Paint();
            trajectoryHandPaint.setColor(Color.rgb(164,156,133));
            trajectoryHandPaint.setStrokeWidth(1f);
            trajectoryHandPaint.setAntiAlias(true);
            trajectoryHandPaint.setAlpha(150);
            trajectoryHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */

            mHourHandLength = 0.5f * width / 2;
            mMinuteHandLength = 0.7f * width / 2;
            mSecondHandLength = 0.9f * width / 2;

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);
        }

        class handRotations {
            public float secondsRotation;
            public float hoursRotation;
            public float minutesRotation;
            public float hourHandOffset;
        }

        public handRotations getRotationsForTime(Time t) {
            handRotations r = new handRotations();
            r.secondsRotation = t.second * 6f;
            r.minutesRotation = t.minute * 6f;
            r.hourHandOffset = t.minute / 2f;
            r.hoursRotation = (t.hour * 30) + r.hourHandOffset;
            return r;
        }

        Time addMinutesToTime(Time t, Integer addMinutes){
            Time resultTime = new Time();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.toMillis(false));
            c.add(MINUTE, addMinutes);
            resultTime.set(c.getTimeInMillis());
            return resultTime;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);

            handRotations r = new handRotations();

            Time shiftedTime = addMinutesToTime(mTime, 75);

            drawHandsForTime(canvas, shiftedTime, trajectoryHandPaint);
            drawHandsForTime(canvas, mTime, mHandPaint);

            canvas.drawCircle(mCenterX, mCenterY, HAND_END_CAP_RADIUS, mHandPaint);
        }

        private void drawHandsForTime(Canvas canvas, Time t, Paint paint){
            canvas.save();
            handRotations r;
            r = getRotationsForTime(t);
            drawHandsForRotations(canvas, r, paint);
            canvas.restore();
        }

        private void drawHandsForRotations(Canvas canvas, handRotations r, Paint paint){
            canvas.rotate(r.hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mHourHandLength, paint);

            canvas.rotate(r.minutesRotation - r.hoursRotation, mCenterX, mCenterY);
            drawHand(canvas, mMinuteHandLength, paint);

            if(!mAmbient){
            canvas.rotate(r.secondsRotation - r.minutesRotation, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY - HAND_END_CAP_RADIUS, mCenterX,
                    mCenterY - mSecondHandLength, paint);
            }
        }

        private void drawHand(Canvas canvas, float handLength, Paint paint) {
            canvas.drawRoundRect(mCenterX - HAND_END_CAP_RADIUS,
                    mCenterY - handLength, mCenterX + HAND_END_CAP_RADIUS,
                    mCenterY + HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, paint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            /*
             * Whether the timer should be running depends on whether we're visible
             * (as well as whether we're in ambient mode),
             * so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
