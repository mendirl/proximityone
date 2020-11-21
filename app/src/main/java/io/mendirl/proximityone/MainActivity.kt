package io.mendirl.proximityone

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.mendirl.proximityone.service.LastKnownLocationService
import io.mendirl.proximityone.service.LastKnownLocationService.Companion.ACTION_BROADCAST
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }

    private val mainScope = MainScope()

    // The BroadcastReceiver used to listen from broadcasts from the service.
    private lateinit var lastKnownLocationServiceReceiver: LastKnownLocationServiceReceiver

    private lateinit var vibratorService: Vibrator

    private var lastKnownLocationService: LastKnownLocationService? = null
    private var isBound: Boolean = false
    private val api = OpenStreetMapGeoCodingService()


    private var address: GeoPosition? = null


    // Monitors the state of the connection to the service.
    private val lastKnownLocationServiceConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder: LastKnownLocationService.LocalBinder = service as LastKnownLocationService.LocalBinder
                lastKnownLocationService = binder.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                lastKnownLocationService = null
                isBound = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        lastKnownLocationServiceReceiver = LastKnownLocationServiceReceiver()
        vibratorService = getSystemService(VIBRATOR_SERVICE) as Vibrator

        setContentView(R.layout.activity_main)
    }


    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()

        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(
            Intent(this, LastKnownLocationService::class.java),
            lastKnownLocationServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        Log.i(TAG, "onResume")
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(lastKnownLocationServiceReceiver, IntentFilter(ACTION_BROADCAST))
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        // commented to keep handling notification when the phone is locked
        // LocalBroadcastManager.getInstance(this).unregisterReceiver(lastKnownLocationServiceReceiver)
        super.onPause()
    }

    override fun onStop() {
        Log.i(TAG, "onStop")
        if (isBound) {
            Log.i(TAG, "serviceconnection is bounded")
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(lastKnownLocationServiceConnection)
            isBound = false
        }

        super.onStop()
    }

    fun showNewAddressDialog(view: View) {
        Log.i(TAG, "showNewAddressDialog")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Address")

        // Get the layout inflater
        val dialogView = layoutInflater.inflate(R.layout.dialog_signin, null)

        builder.setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                sendDialogDataToActivity(
                    dialogView.findViewById<EditText>(
                        R.id.new_adress
                    ).text.toString(), this
                )
            }
            .setNegativeButton(R.string.nok) { dialog, _ -> dialog.cancel() }

        builder.create().show()
    }


    private fun sendDialogDataToActivity(data: String, mainActivity: MainActivity) {
        Log.i(TAG, "sendDialogDataToActivity")
        mainScope.launch {
            kotlin.runCatching {
                api.info(data)
            }.onSuccess {
                address = it[0]
                putInPreferences(address)
                Toast.makeText(mainActivity, address?.displayName, Toast.LENGTH_SHORT).show()
                populate(address)
            }.onFailure {
                Toast.makeText(mainActivity, "problem with $data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun putInPreferences(address: GeoPosition?) {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putFloat(getString(R.string.saved_address_latitude), address?.latitude?.toFloat()!!)
            putFloat(getString(R.string.saved_address_longitude), address.longitude.toFloat())
            putString(getString(R.string.saved_address_displayName), address.displayName)
            apply()
        }
    }

    private fun populate(address: GeoPosition?) {
        Log.i(TAG, "populate")
        val displayNameTextView = findViewById<TextView>(R.id.displayName)
        val latitudeTextView = findViewById<TextView>(R.id.addressLatitude)
        val longitudeTextView = findViewById<TextView>(R.id.addressLongitude)

        displayNameTextView.text = baseContext.getString(R.string.address_displayName_field, address?.displayName)
        latitudeTextView.text = baseContext.getString(R.string.latitude_field, address?.latitude)
        longitudeTextView.text = baseContext.getString(R.string.longitude_field, address?.longitude)

        if (!checkPermissions()) {
            Log.i(TAG, "permission ko")
            requestPermissions()
        } else {
            Log.i(TAG, "permission ok")
            lastKnownLocationService?.requestLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionsResult")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermissions()) {
            Log.i(TAG, "Permission was granted")
            lastKnownLocationService?.requestLocation()
        }
    }

    private fun checkPermissions(): Boolean {
        Log.i(TAG, "checkPermissions")
        return checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) && checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun checkPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        Log.i(TAG, "requestPermissions")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            PackageManager.PERMISSION_GRANTED
        )
    }

    private fun updateLocation(location: Location) {
        Log.i(TAG, "updateLocation")
        val positionLatitudeTextView = findViewById<TextView>(R.id.positionLatitude)
        val positionLongitudeTextView = findViewById<TextView>(R.id.positionLongitude)

        positionLatitudeTextView.text = baseContext.getString(R.string.latitude_field, location.latitude)
        positionLongitudeTextView.text = baseContext.getString(R.string.longitude_field, location.longitude)

        if (address == null) {
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            val prefLatitude = sharedPref.getFloat(getString(R.string.saved_address_latitude), 0F)
            val prefLongitude = sharedPref.getFloat(getString(R.string.saved_address_longitude), 0F)
            val prefDisplayName = sharedPref.getString(getString(R.string.saved_address_displayName), "")

            address = GeoPosition(prefLatitude.toDouble(), prefLongitude.toDouble(), prefDisplayName.toString())
            populate(address)
        }

        val result = FloatArray(1)
        Location.distanceBetween(
            address?.latitude!!, address?.longitude!!,
            location.latitude, location.longitude,
            result
        )

        tooFarFromHome(result[0].toInt())

        val distanceTextView = findViewById<TextView>(R.id.distance)
        distanceTextView.text = baseContext.getString(R.string.distance, result[0].toInt())
    }

    private fun tooFarFromHome(distance: Int) {
        if (distance > 1000) {
            Log.w(TAG, "too far from home: $distance")
            vibratorService.vibrate(VibrationEffect.createOneShot(3000, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    /**
     * Receiver for broadcasts sent by [LastKnownLocationService].
     */
    inner class LastKnownLocationServiceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "LastKnownLocationServiceReceiver:onReceive")
            val location = intent.getParcelableExtra<Location>(LastKnownLocationService.EXTRA_LOCATION)
            if (location != null) {
                this@MainActivity.updateLocation(location)
            }
        }
    }

}
