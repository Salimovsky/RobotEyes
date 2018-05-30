package com.addi.salim.robot_eyes;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

public class PermissionUtil {
    private final static String TAG = PermissionUtil.class.getSimpleName();

    // Constant for permission request codes
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    public static final int LOCATION_SETTING_REQUEST_CODE = 3;
    public static final int BLUETOOTH_SETTING_REQUEST_CODE = 4;
    public static final int RC_HANDLE_GMS = 5;


    public static boolean isLocationPermissionGranted(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager
                .PERMISSION_GRANTED;
    }

    /**
     * Method to request location permission
     *
     * @param activity    activity context
     * @param requestCode request code for location permission
     */
    public static void requestLocationPermission(@NonNull Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                requestCode);
    }

    /**
     * Return a boolean saying if the Location services are enabled or not in settings
     *
     * @param context the activity context
     * @return true if Location services is enabled, false if disabled
     */
    public static boolean isLocationEnabled(@NonNull Context context) {
        int locationMode = Settings.Secure.LOCATION_MODE_OFF;
        try {
            locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    }

    public static void displayNetworkProvidedLocationSettingRequest(@NonNull final Activity activity, final int requestCode) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(activity).addApi(LocationServices.API).build();
        googleApiClient.connect();

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(activity).checkLocationSettings(builder.build());
        task.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.
                    Log.i(TAG, "All location settings are satisfied.");
                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to upgrade location settings ");
                                // Cast to a resolvable exception.
                                ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(activity, requestCode);

                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            } catch (ClassCastException e) {
                                // Ignore, should be an impossible error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog not created.");
                            break;
                    }
                }
            }
        });
    }
}
