// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ng.dat.ar.test.findnearbyplacesar

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import ng.dat.ar.ARActivity
import ng.dat.ar.R
import ng.dat.ar.test.findnearbyplacesar.api.PlacesService
import ng.dat.ar.test.findnearbyplacesar.ar.PlaceNode
import ng.dat.ar.test.findnearbyplacesar.ar.PlacesArFragment
import ng.dat.ar.test.findnearbyplacesar.model.Geometry
import ng.dat.ar.test.findnearbyplacesar.model.GeometryLocation
import ng.dat.ar.test.findnearbyplacesar.model.Place
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener,
    ConnectionCallbacks, OnConnectionFailedListener {
    var previousNode: AnchorNode? = null

    private lateinit var locationManager: LocationManager
    var location: Location? = null
    var isGPSEnabled = false
    var isNetworkEnabled = false
    var locationServiceAvailable = false
    private var azimuth: Int = 0
    private var rotationV: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var rMat = FloatArray(9)
    private var orientation = FloatArray(3)
    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    private var isArPlaced = false


    private val TAG = "MainActivity"

    private lateinit var placesService: PlacesService
    private lateinit var arFragment: PlacesArFragment
    private lateinit var mapFragment: SupportMapFragment

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var anchorNode: AnchorNode? = null
    private var markers: MutableList<Marker> = emptyList<Marker>().toMutableList()
    private var places: List<Place>? = listOf(
        Place(
            "1", "", "Name",
            Geometry(GeometryLocation(30.101093490206033, 31.312743384684453))
        )
    )
    private var currentLocation: Location? = null
    private var map: GoogleMap? = null
    private lateinit var camera: Camera
    private lateinit var scene: Scene

    private var mGoogleApiClient: GoogleApiClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isSupportedDevice()) {
            return
        }
        setContentView(R.layout.activity_main)
        onSetGoogleApiClient()
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as
                PlacesArFragment
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.maps_fragment) as SupportMapFragment

        sensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager

        arFragment.planeDiscoveryController.hide()
        arFragment.planeDiscoveryController.setInstructionView(null)
        scene = arFragment.arSceneView.scene!!
        camera = scene.camera

        placesService = PlacesService.create()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        setUpAr()
        setUpMaps()
    }

    private fun onSetGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build()
        }
    }

    private fun registerSensors() {
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
        initLocationService()
    }

    private fun initLocationService() {
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            this.locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

            // Get GPS and network status
            this.isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            this.isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!isNetworkEnabled && !isGPSEnabled) {
                // cannot get location
                this.locationServiceAvailable = false
            }
            this.locationServiceAvailable = true
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    ARActivity.MIN_TIME_BW_UPDATES,
                    ARActivity.MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat(), this
                )
                if (locationManager != null) {
                    location =
                        locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    updateLatestLocation()
                }
            }
            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    ARActivity.MIN_TIME_BW_UPDATES,
                    ARActivity.MIN_DISTANCE_CHANGE_FOR_UPDATES.toFloat(), this
                )
                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    updateLatestLocation()
                }
            }
        } catch (ex: Exception) {
            Log.e("ARActivity.TAG", ex.message)
        }
    }

    private fun updateLatestLocation() {
        location?.let {
            if (!isArPlaced) {
                isArPlaced = true
                placeARByLocation(it, LatLng(it.latitude, it.longitude), "Vanak")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun setUpAr() {
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            // Create anchor
            val anchor = hitResult.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode?.setParent(arFragment.arSceneView.scene)
        }
    }


    private fun showInfoWindow(place: Place) {
        // Show in AR
        val matchingPlaceNode = anchorNode?.children?.filter {
            it is PlaceNode
        }?.first {
            val otherPlace = (it as PlaceNode).place ?: return@first false
            return@first otherPlace == place
        } as? PlaceNode
        matchingPlaceNode?.showInfoWindow()

        // Show as marker
        val matchingMarker = markers.firstOrNull {
            val placeTag = (it.tag as? Place) ?: return@firstOrNull false
            return@firstOrNull placeTag == place
        }
        matchingMarker?.showInfoWindow()
    }


    private fun setUpMaps() {
        mapFragment.getMapAsync { googleMap ->
            googleMap.isMyLocationEnabled = true

            getCurrentLocation {
                val pos = CameraPosition.fromLatLngZoom(it.latLng, 13f)
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
//                getNearbyPlaces(it)
            }
            googleMap.setOnMarkerClickListener { marker ->
                val tag = marker.tag
                if (tag !is Place) {
                    return@setOnMarkerClickListener false
                }
                showInfoWindow(tag)
                return@setOnMarkerClickListener true
            }
            map = googleMap
        }
    }

    private fun getCurrentLocation(onSuccess: (Location) -> Unit) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
            onSuccess(location)
        }.addOnFailureListener {
            Log.e(TAG, "Could not get location")
        }
    }
//
//    private fun getNearbyPlaces(location: Location) {
//        val apiKey = this.getString(R.string.google_maps_key)
//        placesService.nearbyPlaces(
//            apiKey = apiKey,
//            location = "${location.latitude},${location.longitude}",
//            radiusInMeters = 2000,
//            placeType = "park"
//        ).enqueue(
//            object : Callback<NearbyPlacesResponse> {
//                override fun onFailure(call: Call<NearbyPlacesResponse>, t: Throwable) {
//                    Log.e(TAG, "Failed to get nearby places", t)
//                }
//
//                override fun onResponse(
//                    call: Call<NearbyPlacesResponse>,
//                    response: Response<NearbyPlacesResponse>
//                ) {
//                    if (!response.isSuccessful) {
//                        Log.e(TAG, "Failed to get nearby places")
//                        return
//                    }
//
//                    val places = response.body()?.results ?: emptyList()
////                    this@MainActivity.places = places
//                }
//            }
//        )
//    }


    private fun isSupportedDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val openGlVersionString = activityManager.deviceConfigurationInfo.glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            finish()
            return false
        }
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
//        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
//            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
//        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
//            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
//        }
//
//        // Update rotation matrix, which is needed to update orientation angles.
//        SensorManager.getRotationMatrix(
//            rotationMatrix,
//            null,
//            accelerometerReading,
//            magnetometerReading
//        )
//        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(
                rMat, event.values
            )
            azimuth = (Math.toDegrees(
                SensorManager.getOrientation(
                    rMat, orientation
                )[0].toDouble()
            ) + 360).toInt() % 360
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            SensorManager.getRotationMatrix(rMat, null, lastAccelerometer, lastMagnetometer)
            SensorManager.getOrientation(rMat, orientation)
            azimuth = (Math.toDegrees(
                SensorManager.getOrientation(
                    rMat,
                    orientation
                )[0].toDouble()
            ) + 360).toInt() % 360
        }

//        if (!isArPlaced) {
//            isArPlaced = true
//            Handler().postDelayed({
//                SmartLocation.with(this).location().start {
//                    placeARByLocation(it, LatLng(30.101299791268172, 31.31293679135109), "Vanak")
////                    placeARByLocation(it, LatLng(35.777144, 51.409349), "Mellat")
////                    placeARByLocation(it, LatLng(35.765259, 51.419390), "Sattari")
//                }
//            }, 2)
//        }
    }


    private fun placeARByLocation(myLocation: Location, targetLocation: LatLng, name: String) {
        val tLocation = Location("")
        tLocation.latitude = targetLocation.latitude
        tLocation.longitude = targetLocation.longitude

        val degree = (360 - (bearing(myLocation, tLocation) * 180 / PI))
        val distant = 3.0

        val y = 0.0
        val x = distant * cos(PI * degree / 180)
        val z = -1 * distant * sin(PI * degree / 180)
        addPointByXYZ(x.toFloat(), y.toFloat(), z.toFloat(), name)

        Log.i("ARCore_MyLat", myLocation.latitude.toString())
        Log.i("ARCore_MyLon", myLocation.longitude.toString())
        Log.i("ARCore_TargetLat", targetLocation.latitude.toString())
        Log.i("ARCore_TargetLon", targetLocation.longitude.toString())
        Log.i("ARCore_COMPASS", azimuth.toString())
        Log.i("ARCore_Degree", degree.toString())
        Log.i("ARCore_X", x.toString())
        Log.i("ARCore_Y", y.toString())
        Log.i("ARCore_Z", z.toString())

    }

    private fun bearing(locA: Location, locB: Location): Double {
        val latA = locA.latitude * PI / 180
        val lonA = locA.longitude * PI / 180
        val latB = locB.latitude * PI / 180
        val lonB = locB.longitude * PI / 180

        val deltaOmega = ln(tan((latB / 2) + (PI / 4)) / tan((latA / 2) + (PI / 4)))
        val deltaLongitude = abs(lonA - lonB)

        return atan2(deltaLongitude, deltaOmega)
    }

    private fun addPointByXYZ(x: Float, y: Float, z: Float, name: String) {
        ViewRenderable.builder().setView(this, R.layout.sample_layout).build()
            .thenAccept {
                val imageView = it.view.findViewById<ImageView>(R.id.imageViewSample)
                val textView = it.view.findViewById<TextView>(R.id.textViewSample)

                textView.text = name
//                if (previousNode!=null)
//                scene.removeChild(previousNode)

                val node = AnchorNode()
                node.renderable = it
                scene.addChild(node)
                node.worldPosition = Vector3(x, y, z)

                val cameraPosition = scene.camera.worldPosition
                val direction = Vector3.subtract(cameraPosition, node.worldPosition)
                val lookRotation = Quaternion.lookRotation(direction, Vector3.up())
                node.worldRotation = lookRotation
//                previousNode = node
            }
    }

    override fun onLocationChanged(location: Location?) {

        this.location = location
        updateLatestLocation()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String?) {
    }

    override fun onProviderDisabled(provider: String?) {
    }

    override fun onConnected(p0: Bundle?) {
    }

    override fun onConnectionSuspended(p0: Int) {
    }

    override fun onConnectionFailed(p0: ConnectionResult) {
    }
}

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)

