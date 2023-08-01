package com.hoho.android.usbserial.examples;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;

public class LocationHelper {
    private LocationManager locationManager;
    private LocationListener locationListener;
    private LocationCallback locationCallback;
    private Handler handler;

    public interface LocationCallback {
        void onLocationReceived(Location location);
    }

    public LocationHelper(@NonNull Context context, @Nullable LocationCallback callback) {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationCallback = callback;
        initializeLocationListener();
        handler = new Handler();
    }

    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                            1000L, // Minimum time interval between updates (in milliseconds)
                            1F, // Minimum distance change between updates (in meters)
                            locationListener);
                }
            });
        }
    }

    public void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            handler.removeCallbacksAndMessages(null);
            locationManager.removeUpdates(locationListener);
        }
    }

    private void initializeLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (locationCallback != null) {
                    locationCallback.onLocationReceived(location);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
    }
}
