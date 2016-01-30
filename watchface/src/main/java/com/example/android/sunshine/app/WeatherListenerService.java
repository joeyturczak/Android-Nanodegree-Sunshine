package com.example.android.sunshine.app;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by joeyturczak on 1/20/16.
 * Copyright (C) 2015 Joey Turczak
 */
public class WeatherListenerService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = WeatherListenerService.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataMap weatherKeysToOverwrite = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if (!path.equals(Utility.PATH)) {
                    return;
                }
                // It's allowed that the message carries only some of the keys used in the weather DataItem
                // and skips the ones that we don't want to change.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Received watch face weather message: " + weatherKeysToOverwrite);
                }

                if (mGoogleApiClient == null) {
                    mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
                }
                if (!mGoogleApiClient.isConnected()) {
                    ConnectionResult connectionResult =
                            mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

                    if (!connectionResult.isSuccess()) {
                        Log.e(TAG, "Failed to connect to GoogleApiClient.");
                        return;
                    }
                }
                Utility.overwriteKeysInWeatherDataMap(mGoogleApiClient, weatherKeysToOverwrite);
            }
        }
    }

    @Override // GoogleApiClient.ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnected: " + connectionHint);
        }
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
