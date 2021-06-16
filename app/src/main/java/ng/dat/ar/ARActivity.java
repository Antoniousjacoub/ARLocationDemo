package ng.dat.ar;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import ng.dat.ar.helper.LocationHelper;
import ng.dat.ar.model.Step;

import static android.hardware.SensorManager.*;
import static android.view.Surface.*;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static ng.dat.ar.helper.Constants.DESTINATION_LAT;
import static ng.dat.ar.helper.Constants.DESTINATION_LOG;
import static ng.dat.ar.helper.LocationHelper.openGoogleMaps;

public class ARActivity extends AppCompatActivity implements SensorEventListener, LocationListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private GoogleApiClient mGoogleApiClient;

    final static String TAG = "ARActivity";
    private SurfaceView surfaceView;
    private FrameLayout cameraContainerLayout;
    private TextView tvDistance;
    private AROverlayView arOverlayView;
    private Camera camera;
    private ARCamera arCamera;
    private TextView tvCurrentLocation;
    private TextView tvBearing;
    private Button btnGoogleMaps;
    private Step steps[];
    private SensorManager sensorManager;
    private boolean isDestinationPopupShowed = false;
    private final static int REQUEST_CAMERA_PERMISSIONS_CODE = 11;
    public static final int REQUEST_LOCATION_PERMISSIONS_CODE = 0;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 10 meters
    private static final long MIN_TIME_BW_UPDATES = 0;//1000 * 60 * 1; // 1 minute

    private LocationManager locationManager;
    public Location location;
    boolean isGPSEnabled;
    boolean isNetworkEnabled;
    boolean locationServiceAvailable;
    private float declination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        cameraContainerLayout = findViewById(R.id.camera_container_layout);
        tvDistance = findViewById(R.id.tvDistance);
        surfaceView = findViewById(R.id.surface_view);
        tvCurrentLocation = findViewById(R.id.tv_current_location);
        tvBearing = findViewById(R.id.tv_bearing);
        btnGoogleMaps = findViewById(R.id.btnGoogleMaps);
        arOverlayView = new AROverlayView(this);
        onSetGoogleApiClient();
    }

    @Override
    public void onResume() {
        super.onResume();
        requestCameraPermission();
        requestLocationPermission();
        registerSensors();
        initAROverlayView();
    }

    private void onSetGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10);
        mLocationRequest.setFastestInterval(50);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @Override
    public void onPause() {
        releaseCamera();
        super.onPause();
    }

    public void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSIONS_CODE);
        } else {
            initARCameraView();
        }
    }

    public void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSIONS_CODE);
        } else {
            initLocationService();
        }
    }

    public void initAROverlayView() {
        if (arOverlayView.getParent() != null) {
            ((ViewGroup) arOverlayView.getParent()).removeView(arOverlayView);
        }
        cameraContainerLayout.addView(arOverlayView);
    }

    public void initARCameraView() {
        reloadSurfaceView();

        if (arCamera == null) {
            arCamera = new ARCamera(this, surfaceView);
        }
        if (arCamera.getParent() != null) {
            ((ViewGroup) arCamera.getParent()).removeView(arCamera);
        }
        cameraContainerLayout.addView(arCamera);
        arCamera.setKeepScreenOn(true);
        initCamera();
    }

    private void initCamera() {
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                camera = Camera.open();
                camera.startPreview();
                arCamera.setCamera(camera);
            } catch (RuntimeException ex) {
                Toast.makeText(this, "Camera not found", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void reloadSurfaceView() {
        if (surfaceView.getParent() != null) {
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
        }

        cameraContainerLayout.addView(surfaceView);
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            arCamera.setCamera(null);
            camera.release();
            camera = null;
        }
    }

    private void registerSensors() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        if (arCamera == null) return;
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrixFromVector = new float[16];
            float[] rotationMatrix = new float[16];
            getRotationMatrixFromVector(rotationMatrixFromVector, sensorEvent.values);
            final int screenRotation = this.getWindowManager().getDefaultDisplay()
                    .getRotation();

            switch (screenRotation) {
                case ROTATION_90:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_Y,
                            AXIS_MINUS_X, rotationMatrix);
                    break;
                case ROTATION_270:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_MINUS_Y,
                            AXIS_X, rotationMatrix);
                    break;
                case ROTATION_180:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_MINUS_X, AXIS_MINUS_Y,
                            rotationMatrix);
                    break;
                default:
                    remapCoordinateSystem(rotationMatrixFromVector,
                            AXIS_X, AXIS_Y,
                            rotationMatrix);
                    break;
            }

            float[] projectionMatrix = arCamera.getProjectionMatrix();
            float[] rotatedProjectionMatrix = new float[16];
            Matrix.multiplyMM(rotatedProjectionMatrix, 0, projectionMatrix, 0, rotationMatrix, 0);
            this.arOverlayView.updateRotatedProjectionMatrix(rotatedProjectionMatrix);

            //Heading
            float[] orientation = new float[3];
            getOrientation(rotatedProjectionMatrix, orientation);
            double bearing = Math.toDegrees(orientation[0]) + declination;
            tvBearing.setText(String.format("Bearing: %s", bearing));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.w("DeviceOrientation", "Orientation compass unreliable");
        }
    }

    private void initLocationService() {

        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            this.locationManager = (LocationManager) this.getSystemService(this.LOCATION_SERVICE);

            // Get GPS and network status
            this.isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            this.isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isNetworkEnabled && !isGPSEnabled) {
                // cannot get location
                this.locationServiceAvailable = false;
            }

            this.locationServiceAvailable = true;

            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    updateLatestLocation();
                }
            }

            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    updateLatestLocation();
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());

        }
    }

    private void updateLatestLocation() {
        if (arOverlayView != null && location != null) {
            arOverlayView.updateCurrentLocation(location);
            tvCurrentLocation.setText(String.format("lat: %s \nlon: %s \naltitude: %s \n",
                    location.getLatitude(), location.getLongitude(), location.getAltitude()));
            setDistance();
            checkTheSameLocation();
            setListenerOnGoogleMapButton();
        }
    }

    private void setListenerOnGoogleMapButton() {
        btnGoogleMaps.setVisibility(View.VISIBLE);
        btnGoogleMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (location == null) return;
                LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                LatLng destLatLng = new LatLng(DESTINATION_LAT, DESTINATION_LOG);
                openGoogleMaps(ARActivity.this, currentLatLng, destLatLng);
            }
        });

    }

    @Override
    public void onLocationChanged(Location location) {
        // Toast.makeText(this,location.getLatitude()+","+location.getLongitude(),Toast.LENGTH_LONG).show();
        this.location = location;
        updateLatestLocation();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    private void setDistance() {
        if (location == null) return;
        tvDistance.setVisibility(View.VISIBLE);
        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        LatLng destLatLng = new LatLng(DESTINATION_LAT, DESTINATION_LOG);
        tvDistance.setText(LocationHelper.calculationByDistance(currentLatLng, destLatLng));
    }

    private void checkTheSameLocation() {
        if (location == null) return;

        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        LatLng destLatLng = new LatLng(DESTINATION_LAT, DESTINATION_LOG);
        if (LocationHelper.distanceDiff(currentLatLng, destLatLng) < 15 && !isDestinationPopupShowed) {
            Toast.makeText(this, getString(R.string.destinationReached)
                    , Toast.LENGTH_LONG).show();
            isDestinationPopupShowed = true;

            onChangeDestinationIcon();
        }


    }

    private void onChangeDestinationIcon() {
        arOverlayView.setDestinationIcon(R.drawable.ic_opened_gift);
        arOverlayView.invalidate();
    }
}
