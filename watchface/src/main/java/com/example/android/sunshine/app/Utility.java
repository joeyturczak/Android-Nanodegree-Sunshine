package com.example.android.sunshine.app;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by joeyturczak on 1/20/16.
 * Copyright (C) 2015 Joey Turczak
 */
public class Utility {

    public static final String WATCH_DATE_FORMAT = "EEE, MMM dd yyyy";

    public static final String PATH = "/weather";
    public static final String HIGH = "high";
    public static final String LOW = "low";
    public static final String ICON = "icon";

    public static final String HIGH_DEFAULT_VALUE = "";
    public static final String LOW_DEFAULT_VALUE = "";

    public static final String TAG = Utility.class.getSimpleName();

    /**
     * Callback interface to perform an action with the current weather {@link DataMap} for
     * {@link MyWatchFace}.
     */
    public interface FetchWeatherDataMapCallback {
        /**
         * Callback invoked with the current weather {@link DataMap} for
         * {@link MyWatchFace}.
         */
        void onWeatherDataMapFetched(DataMap weather);
    }

    /**
     * Asynchronously fetches the current weather {@link DataMap} for {@link MyWatchFace}
     * and passes it to the given callback.
     * <p>
     * If the current weather {@link DataItem} doesn't exist, it isn't created and the callback
     * receives an empty DataMap.
     */
    public static void fetchWeatherDataMap(final GoogleApiClient client,
                                          final FetchWeatherDataMapCallback callback) {
        Wearable.NodeApi.getLocalNode(client).setResultCallback(
                new ResultCallback<NodeApi.GetLocalNodeResult>() {
                    @Override
                    public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                        String localNode = getLocalNodeResult.getNode().getId();
                        Uri uri = new Uri.Builder()
                                .scheme("wear")
                                .path(PATH)
                                .authority(localNode)
                                .build();
                        Wearable.DataApi.getDataItem(client, uri)
                                .setResultCallback(new DataItemResultCallback(callback));
                    }
                }
        );
    }

    /**
     * Overwrites (or sets, if not present) the keys in the current weather {@link DataItem} with
     * the ones appearing in the given {@link DataMap}. If the weather DataItem doesn't exist,
     * it's created.
     * <p>
     * It is allowed that only some of the keys used in the weather DataItem appear in
     * {@code weatherKeysToOverwrite}. The rest of the keys remains unmodified in this case.
     */
    public static void overwriteKeysInWeatherDataMap(final GoogleApiClient googleApiClient,
                                                    final DataMap weatherKeysToOverwrite) {

        Utility.fetchWeatherDataMap(googleApiClient,
                new FetchWeatherDataMapCallback() {
                    @Override
                    public void onWeatherDataMapFetched(DataMap currentWeather) {
                        DataMap overwrittenWeather = new DataMap();
                        overwrittenWeather.putAll(currentWeather);
                        overwrittenWeather.putAll(weatherKeysToOverwrite);
                        Utility.putWeatherDataItem(googleApiClient, overwrittenWeather);
                    }
                }
        );
    }

    /**
     * Overwrites the current weather {@link DataItem}'s {@link DataMap} with {@code newWeather}.
     * If the weather DataItem doesn't exist, it's created.
     */
    public static void putWeatherDataItem(GoogleApiClient googleApiClient, DataMap newWeather) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH);
        putDataMapRequest.setUrgent();
        DataMap weatherToPut = putDataMapRequest.getDataMap();
        weatherToPut.putAll(newWeather);
        Wearable.DataApi.putDataItem(googleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    private static class DataItemResultCallback implements ResultCallback<DataApi.DataItemResult> {

        private final FetchWeatherDataMapCallback mCallback;

        public DataItemResultCallback(FetchWeatherDataMapCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onResult(DataApi.DataItemResult dataItemResult) {
            if (dataItemResult.getStatus().isSuccess()) {
                if (dataItemResult.getDataItem() != null) {
                    DataItem weatherDataItem = dataItemResult.getDataItem();
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(weatherDataItem);
                    DataMap weather = dataMapItem.getDataMap();
                    mCallback.onWeatherDataMapFetched(weather);
                } else {
                    mCallback.onWeatherDataMapFetched(new DataMap());
                }
            }
        }
    }

    public static String getCurrentDateString() {
        Date date = new Date(System.currentTimeMillis());

        SimpleDateFormat dateFormat = new SimpleDateFormat(Utility.WATCH_DATE_FORMAT);

        return dateFormat.format(date).toUpperCase();
    }
}
