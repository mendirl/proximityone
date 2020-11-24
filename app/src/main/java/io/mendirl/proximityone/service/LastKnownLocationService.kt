package io.mendirl.proximityone.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.mendirl.proximityone.GeoPosition
import io.mendirl.proximityone.MainActivity
import io.mendirl.proximityone.R
import io.mendirl.proximityone.Utils


class LastKnownLocationService : Service() {

    companion object {
        private val TAG: String = LastKnownLocationService::class.java.simpleName

        private const val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        const val ACTION_BROADCAST: String = "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION: String = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION: String = "$PACKAGE_NAME.started_from_notification"

        private const val NOTIFICATION_ID = 12345678
        private const val CHANNEL_ID = "channel_01"

        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    }

    private var distanceFromStart: Int? = null
    private var isTooFar: Boolean = false
    private var startPosition: GeoPosition? = null

    private val mBinder: IBinder = LocalBinder()
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var location: Location
    private lateinit var serviceHandler: Handler

    private lateinit var notificationManager: NotificationManager
    private lateinit var vibratorService: Vibrator


    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    override fun onCreate() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationCallback()
        createLocationRequest()
        lastLocation()
        createServiceHandler()
        createNotificationManager()
        createVibratorManager()
    }

    private fun createLocationCallback() {
        locationCallback =
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

    private fun createServiceHandler() {
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        serviceHandler = Handler(handlerThread.looper)
    }

    private fun createNotificationManager() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val appName = getString(R.string.app_name)
        val notificationChannel = NotificationChannel(CHANNEL_ID, appName, IMPORTANCE_DEFAULT)
        notificationChannel.enableVibration(false)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun createVibratorManager() {
        vibratorService = getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

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

        val result = FloatArray(1)
        Location.distanceBetween(
            startPosition?.latitude!!, startPosition?.longitude!!,
            location.latitude, location.longitude,
            result
        )

        distanceFromStart = result[0].toInt()
        tooFarFromHome(distanceFromStart)

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun tooFarFromHome(distance: Int?) {
        if (distance!! > 1000) {
            isTooFar = true
            Log.w(TAG, "too far from home: $distance")
            vibratorService.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE));
            notificationManager.notify(NOTIFICATION_ID, notificationFromLocation())
        } else if (isTooFar) {
            isTooFar = false
            notificationManager.notify(NOTIFICATION_ID, notificationFromLocation())
        }
    }

    private fun notificationFromLocation(): Notification {
        val notificationIntent = Intent(this, LastKnownLocationService::class.java)

        notificationIntent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

        val servicePendingIntent =
            PendingIntent.getService(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val activityPendingIntent =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)

        val locationText = Utils.distanceText(distanceFromStart)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .addAction(R.drawable.ic_launch, getString(R.string.launch_activity), activityPendingIntent)
            .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates), servicePendingIntent)
            .setContentText(locationText)
            .setContentTitle(Utils.locationTitle(this))
            .setOngoing(true)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker(locationText)
            .setWhen(System.currentTimeMillis())

        return builder.build()
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

    override fun onRebind(intent: Intent?) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false

        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service")
            startForeground(NOTIFICATION_ID, notificationFromLocation())
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        serviceHandler.removeCallbacksAndMessages(null)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false)

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocation()
            stopSelf()
        }

        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
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
    fun requestLocation(address: GeoPosition?) {
        Log.i(TAG, "Requesting location updates")
        this.startPosition = address

        Utils.requestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LastKnownLocationService::class.java))
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        } catch (unlikely: SecurityException) {
            Utils.requestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    private fun removeLocation() {
        Log.i(TAG, "Removing location updates")
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Utils.requestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Utils.requestingLocationUpdates(this, true)
            Log.e(TAG, "Lost location permission. Could not remove updates. $unlikely")
        }
    }

}
