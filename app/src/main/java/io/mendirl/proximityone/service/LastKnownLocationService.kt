package io.mendirl.proximityone.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*


class LastKnownLocationService : Service() {

    companion object {
        private val TAG: String = LastKnownLocationService::class.java.simpleName

        private const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        const val ACTION_BROADCAST: String = "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION: String = "$PACKAGE_NAME.location"
        const val EXTRA_STARTED_FROM_NOTIFICATION: String = "$PACKAGE_NAME.started_from_notification"

        const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
        const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    }


    private val mBinder: IBinder = LocalBinder()
    private lateinit var locationCallBack: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private var location: Location? = null

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationCallback()
        createLocationRequest()
        lastLocation()

    }

    private fun createLocationCallback() {
        locationCallBack =
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    onNewLocation(locationResult.lastLocation)
                }
            }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }


//    fusedLocationClient.lastLocation
//    .addOnSuccessListener { location: Location? ->
//        val positionLatitudeTextView = findViewById<TextView>(R.id.positionLatitude)
//        val positionLongitudeTextView = findViewById<TextView>(R.id.positionLongitude)
//
//        positionLatitudeTextView.text =
//            baseContext.getString(R.string.latitude_field, location?.latitude)
//        positionLongitudeTextView.text =
//            baseContext.getString(R.string.longitude_field, location?.longitude)
//    }
//    .addOnFailureListener(this) {
//        Toast.makeText(this, "problem with last location", Toast.LENGTH_SHORT).show()
//    }

    @SuppressLint("MissingPermission")
    private fun lastLocation() {
        fusedLocationClient.lastLocation
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    location = task.result
                } else {
                    Log.w(TAG, "Failed to get location.")
                }
            }
    }

    private fun onNewLocation(location: Location) {
        Log.i(TAG, "New location: $location")

        this.location = location

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }


    override fun onBind(intent: Intent?): IBinder? {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        fun getService(): LastKnownLocationService {
            return this@LastKnownLocationService
        }
    }


    @SuppressLint("MissingPermission")
    fun requestLocation() {
        Log.i(TAG, "Requesting location updates")
        startService(Intent(applicationContext, LastKnownLocationService::class.java))
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallBack, Looper.myLooper())
    }

}