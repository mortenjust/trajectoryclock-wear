/*
 * Copyright (C) 2014 The Android Open Source Project
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


package mortenjust.com.trajectory2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
//import android.location.LocationListener;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.location.LocationListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

//import android.location.Location;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.common.api.GoogleApiClient.*;
import static java.util.Calendar.MINUTE;


/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 * ConnectionCallbacks, OnConnectionFailedListener, and LocationListener.
 */
public class TrajectoryClock extends CanvasWatchFaceService implements ConnectionCallbacks, OnConnectionFailedListener,
            MessageApi.MessageListener, LocationListener
{
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static String TAG = "TRJ2ClockSer";
    private Location mCurrentLocation;
    private Float mCurrentSpeed;
    private String mLastUpdateTime;
    public Float mDistanceToHome = 0f;
    private Location mHomeLocation;
    private long UPDATE_INTERVAL_MS = 5000;
    private long FASTEST_INTERVAL_MS = 1000;


    private static final String WEAR_MESSAGE_PATH = "/message";
    private GoogleApiClient mApiClient;

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected");
        Wearable.MessageApi.addListener( mApiClient, this );
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FASTEST_INTERVAL_MS);

        LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, locationRequest, this)
                .setResultCallback(new ResultCallback() {
                    @Override
                    public void onResult(Result result) {
                        Log.d(TAG, "on result");
                    }
                });

        Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);

        mHomeLocation = new Location("Home");
        mHomeLocation.setLatitude(37.7612467);
        mHomeLocation.setLongitude(-122.4304178);

        if(location != null){

            String valof  = String.valueOf(location.getLatitude());
            Log.d(TAG, valof);

            float distanceToHome = location.distanceTo(mHomeLocation);
            mDistanceToHome = distanceToHome;
            Log.d(TAG, String.format("Distance to home is %f", distanceToHome));

        } else {
            Log.d(TAG, "weird, location is zero");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private void initGoogleApiClient() {
        mApiClient = new Builder( this )
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks( this )
                .build();

        if( mApiClient != null && !( mApiClient.isConnected() || mApiClient.isConnecting() ) )
            mApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mCurrentSpeed = location.getSpeed();

        Log.d(TAG, "Current speed"+mCurrentSpeed.toString());

        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        // Log.d(TAG, "Location received: " + location.toString());

        mDistanceToHome = location.distanceTo(mHomeLocation);
    }


    @Override
    public void onMessageReceived( final MessageEvent messageEvent ) {

        Log.d(TAG, "Received a message in the watch face");

            //
        // String = new String(messageEvent.getData()) // get a string out of the message
        //  if( messageEvent.getPath().equalsIgnoreCase( WEAR_MESSAGE_PATH ) ) { /// check if message path is the same

    }

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        private static final float HAND_END_CAP_RADIUS = 4f;
        private static final float SHADOW_RADIUS = 6f;

        Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        Paint mHandPaint;
        Paint mTextPaint;
        Paint trajHandPaint;
        boolean mAmbient;
        Time mTime;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;


        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            initGoogleApiClient();

            Log.d(TAG, "created watch face");

            setWatchFaceStyle(new WatchFaceStyle.Builder(TrajectoryClock.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = TrajectoryClock.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));
            final int backgroundResId = R.drawable.custom_background;
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), backgroundResId);

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
          //  mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setStrokeWidth(3f);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTextPaint = new Paint();
            mTextPaint.setColor(Color.rgb(164, 156, 133));
            mTextPaint.setTextSize(20);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setAntiAlias(true);

            trajHandPaint = new Paint();
            trajHandPaint.setColor(Color.rgb(164, 156, 133));
            trajHandPaint.setStrokeWidth(1f);
            trajHandPaint.setAntiAlias(true);
            trajHandPaint.setAlpha(175);
            trajHandPaint.setStrokeCap(Paint.Cap.ROUND);



            mTime = new Time();
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

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
//            if( mApiClient != null )
//                mApiClient.unregisterConnectionCallbacks( this );
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void drawHand(Canvas canvas, float handLength, Paint paint) {
            canvas.drawRoundRect(mCenterX - HAND_END_CAP_RADIUS,
                    mCenterY - handLength, mCenterX + HAND_END_CAP_RADIUS,
                    mCenterY + HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, paint);
        }

        Time addMinutesToTime(Time t, Integer addMinutes){
            Time resultTime = new Time();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t.toMillis(false));
            c.add(MINUTE, addMinutes);
            resultTime.set(c.getTimeInMillis());
            return resultTime;
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



        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);


            Double roundedDistance = new BigDecimal(mDistanceToHome/1000).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
            String distanceText = ""+roundedDistance+" km";

            canvas.drawText(distanceText,
                            bounds.centerX(), 100, mTextPaint);

            // how long does it take to travel mDistanceToHome?
            Float kmToHome = mDistanceToHome/1000;
            Float kmPerHour = 0f;


            if (mCurrentSpeed != null && mCurrentSpeed>1) {
                kmPerHour = mCurrentSpeed * 18 / 5;
            } else {
                // if we're not moving, assume walking if less than 5km to home
                if (kmToHome >= 5) {
                    kmPerHour = 70f;
                }
                if (kmToHome < 5) {
                    kmPerHour = 5f;
                }
               // Log.d(TAG, "Setting surrogate speed to "+kmPerHour.toString());
            }

            Float hoursToHome = kmToHome/kmPerHour;
            Integer minsToHome = Math.round(hoursToHome * 60);
            Time shiftedTime = addMinutesToTime(mTime, minsToHome);
            drawHandsForTime(canvas, shiftedTime, trajHandPaint);
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

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            TrajectoryClock.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            TrajectoryClock.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }



}
