package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by joeyturczak on 1/21/16.
 * Copyright (C) 2015 Joey Turczak
 */
public class UpdateWearableIntentService extends IntentService implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = UpdateWearableIntentService.class.getSimpleName();

    private static final String[] FORECAST_COLUMNS = new String[]{
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    public static final String PATH = "/weather";
    public static final String HIGH = "high";
    public static final String LOW = "low";
    public static final String ICON = "icon";

    private GoogleApiClient mGoogleApiClient;

    public UpdateWearableIntentService() {
        super("UpdateWeatherIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        boolean dataUpdated = intent != null &&
                SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction());
        if (dataUpdated) {

            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
            String location = Utility.getPreferredLocation(this);
            Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                    location, System.currentTimeMillis());
            Cursor cursor = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                    null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
            if (cursor.moveToFirst()) {
                int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                double highCelcius = cursor.getDouble(INDEX_MAX_TEMP);
                double lowCelcius = cursor.getDouble(INDEX_MIN_TEMP);

                int iconId = Utility.getIconResourceForWeatherCondition(weatherId);
                Bitmap icon = BitmapFactory.decodeResource(getResources(), iconId);

                Asset asset = toAsset(icon);

                String high = Utility.formatTemperature(this, highCelcius);
                String low = Utility.formatTemperature(this, lowCelcius);

                sendWeatherData(asset, high, low);

            }
            cursor.close();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
    }

    /**
     * Sends the weather data by adding it to the Data Item store.
     */
    private void sendWeatherData(Asset asset, String high, String low) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(PATH);
        dataMap.getDataMap().putAsset(ICON, asset);
        dataMap.getDataMap().putString(HIGH, high);
        dataMap.getDataMap().putString(LOW, low);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();

        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Log.d(LOG_TAG, "Sending data was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });

    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. Typically, your image
     * should not exceed 320x320 and if you want to have zoom and parallax effect in your app, limit
     * the size of your image to 640x400. Resize your image before transferring to your wearable
     * device.
     */
    public static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnected: " + connectionHint);
        }
    }

    @Override  // GoogleApiClient.ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnectionSuspended: " + cause);
        }
    }

    @Override  // GoogleApiClient.OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnectionFailed: " + result);
        }
    }
}
