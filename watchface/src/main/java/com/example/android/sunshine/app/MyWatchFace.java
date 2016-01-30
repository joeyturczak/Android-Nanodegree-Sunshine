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

package com.example.android.sunshine.app;

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
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Copyright (C) 2015 Joey Turczak
 */

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    private static final String TAG = MyWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mIconPaint;
        Paint mHighPaint;
        Paint mLowPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        Bitmap mIcon;
        String mHigh = Utility.HIGH_DEFAULT_VALUE;
        String mLow = Utility.LOW_DEFAULT_VALUE;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset_round);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.transparent_text));

            mIconPaint = new Paint();
            mIconPaint.setColor(getResources().getColor(R.color.white));

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.transparent_text));

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
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
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_size_round : R.dimen.digital_date_size);

            float tempSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_size_round : R.dimen.digital_temp_size);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateSize);

            mHighPaint.setTextSize(tempSize);
            mLowPaint.setTextSize(tempSize);

            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mHighPaint.setTextAlign(Paint.Align.CENTER);
            mLowPaint.setTextAlign(Paint.Align.CENTER);
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
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void updateWeatherDataItemOnStartup() {
            Utility.fetchWeatherDataMap(mGoogleApiClient,
                    new Utility.FetchWeatherDataMapCallback() {
                        @Override
                        public void onWeatherDataMapFetched(DataMap startupWeather) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingWeatherKeys(startupWeather);
                            Utility.putWeatherDataItem(mGoogleApiClient, startupWeather);

                            updateUiForWeatherDataMap(startupWeather);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingWeatherKeys(DataMap weather) {
            addStringKeyIfMissing(weather, Utility.HIGH,
                    Utility.HIGH_DEFAULT_VALUE);
            addStringKeyIfMissing(weather, Utility.LOW,
                    Utility.LOW_DEFAULT_VALUE);
            addIconIfMissing(weather, Utility.ICON);
        }

        private void addStringKeyIfMissing(DataMap weather, String key, String temp) {
            if (!weather.containsKey(key)) {
                weather.putString(key, temp);
            }
        }

        private void addIconIfMissing(DataMap weather, String key) {
            if (!weather.containsKey(key)) {
                weather.putAsset(key, null);
            }
        }

        private void updateUiForWeatherDataMap(final DataMap weather) {
            boolean uiUpdated = false;
            for (String weatherKey : weather.keySet()) {
                if (!weather.containsKey(weatherKey)) {
                    continue;
                }
                if (weatherKey.equals(Utility.HIGH) || weatherKey.equals(Utility.LOW)) {
                    String temp = weather.getString(weatherKey);
                    if (updateUiForKey(weatherKey, temp)) {
                        uiUpdated = true;
                    }
                } else {
                    Asset asset = weather.getAsset(weatherKey);
                    if (updateIcon(weatherKey, asset)) {
                        uiUpdated = true;
                    }
                }

            }
            if (uiUpdated) {
                invalidate();
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataMap weather = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if (path.equals(Utility.PATH)) {
                        updateUiForWeatherDataMap(weather);
                    }
                }

            }
        }

        /**
         * Updates high and low temperatures to the given {@code weatherKey}. Does nothing if
         * {@code weatherKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String weatherKey, String temp) {
            if (weatherKey.equals(Utility.HIGH)) {
                mHigh = temp;
            } else if (weatherKey.equals(Utility.LOW)) {
                mLow = temp;
            } else {
                Log.w(TAG, "Ignoring unknown weather key: " + weatherKey);
                return false;
            }
            return true;
        }

        /**
         * Updates weather icon and resizes it.
         */
        private boolean updateIcon(String iconKey, Asset asset) {
            if (iconKey.equals(Utility.ICON) && asset != null) {
                new LoadBitmapAsyncTask() {
                    @Override
                    protected void onPostExecute(Bitmap bitmap) {
                        mIcon = Bitmap.createScaledBitmap(bitmap, 80, 80, false);
                    }
                }.execute(asset);
            } else {
                return false;
            }
            return true;
        }

        /*
     * Extracts {@link android.graphics.Bitmap} data from the
     * {@link com.google.android.gms.wearable.Asset}
     */
        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {

                if(params.length > 0) {

                    Asset asset = params[0];

                    if (asset == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return loadBitmapFromAsset(asset);

                } else {
                    Log.e(TAG, "Asset must be non-null");
                    return null;
                }
            }
        }

        private Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                Log.d(TAG, "Cannot load bitmap. Asset is null");
                return null;
            }
            ConnectionResult result = mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();

            return BitmapFactory.decodeStream(assetInputStream);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            float centerWidth = canvas.getWidth() / 2;
            float centerHeight = canvas.getHeight() / 2;
            float separatorXStart = centerWidth - 25;
            float separatorXStop = centerWidth + 25;
            float separatorY = centerHeight + 20;

            Paint linePaint = new Paint();
            linePaint.setColor(getResources().getColor(R.color.transparent_text));
            linePaint.setStrokeWidth(1);

            canvas.drawLine(separatorXStart, separatorY, separatorXStop, separatorY, linePaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, centerWidth, mYOffset, mTextPaint);

            // Draw current date
            canvas.drawText(Utility.getCurrentDateString(), centerWidth, mYOffset + 40, mDatePaint);

            float iconX = canvas.getWidth() / 10;
            float highX = centerWidth;
            float lowX = highX + (centerWidth / 2);
            float tempY = centerHeight + (centerHeight / 2);
            float iconY = centerHeight + (centerHeight / 6);

            canvas.drawText(mHigh, highX, tempY, mHighPaint);
            canvas.drawText(mLow, lowX, tempY, mLowPaint);

            // Draw current weather icon
            if (!isInAmbientMode()) {
                if (mIcon != null) {
                    canvas.drawBitmap(mIcon, iconX, iconY, mIconPaint);
                }
            }


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

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateWeatherDataItemOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }
    }
}
