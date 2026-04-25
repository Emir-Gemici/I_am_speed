package com.tigeronur.iamspeed;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final float HUMAN_TOP_SPEED_KMH = 36f;

    private TextView speedText;
    private TextView gearText;
    private TextView vehicleInfoText;
    private Button startButton;
    private Spinner vehicleSpinner;
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationCallback locationCallback;
    private boolean isLocationUpdatesStarted = false;
    private ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher;
    private List<VehicleProfile> vehicleProfiles;
    private VehicleProfile selectedVehicle;
    private MediaPlayer enginePlayer;
    private SoundPool soundPool;
    private int shiftUpSoundId;
    private int shiftDownSoundId;
    private int currentGear = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        speedText = findViewById(R.id.speedText);
        gearText = findViewById(R.id.gearText);
        vehicleInfoText = findViewById(R.id.vehicleInfoText);
        startButton = findViewById(R.id.startButton);
        vehicleSpinner = findViewById(R.id.vehicleSpinner);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);
        setupAudio();
        setupVehicleSelector();
        locationSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (hasLocationPermission()) {
                        startLocationUpdates();
                    }
                }
        );
        startButton.setOnClickListener(v -> checkLocationPermissionAndStart());
    }

    private void setupAudio() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();

        shiftUpSoundId = loadSoundFromRaw("shift_up");
        shiftDownSoundId = loadSoundFromRaw("shift_down");
    }

    private int loadSoundFromRaw(String resourceName) {
        int resId = getRawResourceId(resourceName);
        if (resId == 0 || soundPool == null) {
            return 0;
        }
        return soundPool.load(this, resId, 1);
    }

    private void setupVehicleSelector() {
        vehicleProfiles = new ArrayList<>();
        vehicleProfiles.add(new VehicleProfile("RS6", "rs6", 305,
                new int[]{0, 25, 50, 85, 120, 165, 210, 255, 305}));
        vehicleProfiles.add(new VehicleProfile("M5 CS", "m5_cs", 305,
                new int[]{0, 22, 48, 82, 118, 160, 205, 250, 305}));
        vehicleProfiles.add(new VehicleProfile("AMG GT 63", "amg_gt_63", 315,
                new int[]{0, 24, 52, 88, 126, 170, 218, 268, 315}));

        ArrayAdapter<VehicleProfile> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                vehicleProfiles
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleSpinner.setAdapter(adapter);
        vehicleSpinner.setSelection(0);

        selectedVehicle = vehicleProfiles.get(0);
        updateVehicleInfo();
        prepareEngineSound();

        vehicleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVehicle = vehicleProfiles.get(position);
                updateVehicleInfo();
                prepareEngineSound();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void checkLocationPermissionAndStart() {
        if (hasLocationPermission()) {
            startLocationUpdates();
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    private void startLocationUpdates() {
        if (isLocationUpdatesStarted || !hasLocationPermission()) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000
        ).setMinUpdateIntervalMillis(500).build();
        checkDeviceLocationSettingsAndStart(locationRequest);
    }

    private void checkDeviceLocationSettingsAndStart(LocationRequest locationRequest) {
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true)
                .build();

        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(settingsRequest);

        task.addOnSuccessListener(locationSettingsResponse ->
                requestLocationUpdates(locationRequest)
        );

        task.addOnFailureListener(exception -> {
            if (exception instanceof ResolvableApiException) {
                IntentSenderRequest intentSenderRequest =
                        new IntentSenderRequest.Builder(
                                ((ResolvableApiException) exception).getResolution()
                        ).build();
                locationSettingsLauncher.launch(intentSenderRequest);
            } else {
                Toast.makeText(this, "Konum servisi kullan\u0131lam\u0131yor.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestLocationUpdates(LocationRequest locationRequest) {
        if (!hasLocationPermission()) {
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    float realSpeedKmh = Math.max(0f, location.getSpeed() * 3.6f);
                    float scaledSpeedKmh = mapHumanSpeedToVehicleSpeed(realSpeedKmh);
                    int newGear = selectedVehicle.getGearForSpeed(scaledSpeedKmh);

                    speedText.setText(Math.round(scaledSpeedKmh) + " km/h");
                    if (newGear != currentGear) {
                        playGearShiftSound(newGear > currentGear);
                        currentGear = newGear;
                    }
                    gearText.setText("Vites: " + currentGear);
                    vehicleInfoText.setText(
                            "Se\u00e7ili ara\u00e7: " + selectedVehicle.name
                                    + " | Ger\u00e7ek h\u0131z: " + Math.round(realSpeedKmh) + " km/h"
                    );
                    updateEngineSound(scaledSpeedKmh);
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            isLocationUpdatesStarted = true;
            startEngineLoop();
        } catch (SecurityException ignored) {
            isLocationUpdatesStarted = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private float mapHumanSpeedToVehicleSpeed(float realSpeedKmh) {
        if (selectedVehicle == null) {
            return realSpeedKmh;
        }

        float scaledSpeed = (realSpeedKmh / HUMAN_TOP_SPEED_KMH) * selectedVehicle.topSpeedKmh;
        return Math.min(scaledSpeed, selectedVehicle.topSpeedKmh);
    }

    private void updateVehicleInfo() {
        if (selectedVehicle == null) {
            return;
        }

        currentGear = 1;
        vehicleInfoText.setText(
                "Se\u00e7ili ara\u00e7: " + selectedVehicle.name + " | Son h\u0131z: "
                        + selectedVehicle.topSpeedKmh + " km/h"
        );
        gearText.setText("Vites: " + currentGear);
    }

    private void prepareEngineSound() {
        releaseEnginePlayer();

        if (selectedVehicle == null) {
            return;
        }

        int resId = getRawResourceId("engine_" + selectedVehicle.soundKey);
        if (resId == 0) {
            resId = getRawResourceId("engine_loop");
        }
        if (resId == 0) {
            return;
        }

        enginePlayer = MediaPlayer.create(this, resId);
        if (enginePlayer == null) {
            return;
        }

        enginePlayer.setLooping(true);
        enginePlayer.setVolume(0.85f, 0.85f);

        if (isLocationUpdatesStarted) {
            startEngineLoop();
        }
    }

    private void startEngineLoop() {
        if (enginePlayer != null && !enginePlayer.isPlaying()) {
            enginePlayer.start();
        }
    }

    private void updateEngineSound(float speedKmh) {
        if (enginePlayer == null || selectedVehicle == null) {
            return;
        }

        float normalizedSpeed = Math.max(0.3f, speedKmh / selectedVehicle.topSpeedKmh);
        float speedRatio = 0.8f + (normalizedSpeed * 1.2f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = enginePlayer.getPlaybackParams();
                params.setSpeed(Math.min(2.0f, speedRatio));
                enginePlayer.setPlaybackParams(params);
            } catch (IllegalStateException ignored) {
            }
        }

        float volume = Math.min(1.0f, 0.35f + normalizedSpeed);
        enginePlayer.setVolume(volume, volume);
    }

    private void playGearShiftSound(boolean isShiftUp) {
        if (soundPool == null) {
            return;
        }

        int soundId = isShiftUp ? shiftUpSoundId : shiftDownSoundId;
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private int getRawResourceId(String resourceName) {
        return getResources().getIdentifier(resourceName, "raw", getPackageName());
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            isLocationUpdatesStarted = false;
        }
        if (enginePlayer != null && enginePlayer.isPlaying()) {
            enginePlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseEnginePlayer();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }

    private void releaseEnginePlayer() {
        if (enginePlayer != null) {
            enginePlayer.release();
            enginePlayer = null;
        }
    }

    private static class VehicleProfile {
        private final String name;
        private final String soundKey;
        private final int topSpeedKmh;
        private final int[] gearLimits;

        private VehicleProfile(String name, String soundKey, int topSpeedKmh, int[] gearLimits) {
            this.name = name;
            this.soundKey = soundKey;
            this.topSpeedKmh = topSpeedKmh;
            this.gearLimits = gearLimits;
        }

        private int getGearForSpeed(float speedKmh) {
            for (int i = 1; i < gearLimits.length; i++) {
                if (speedKmh <= gearLimits[i]) {
                    return i;
                }
            }
            return gearLimits.length - 1;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }
}
