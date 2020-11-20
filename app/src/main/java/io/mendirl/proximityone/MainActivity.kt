package io.mendirl.proximityone

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
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

    private var lastKnownLocationService: LastKnownLocationService? = null
    private var isBound: Boolean = false
    private val api = OpenStreetMapGeoCodingService()

    private lateinit var address: GeoPosition


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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(lastKnownLocationServiceReceiver)
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
                Toast.makeText(mainActivity, address.displayName, Toast.LENGTH_SHORT).show()
                populate(address)
            }.onFailure {
                Toast.makeText(mainActivity, "problem with $data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populate(address: GeoPosition) {
        Log.i(TAG, "populate")
        val displayNameTextView = findViewById<TextView>(R.id.displayName)
        val latitudeTextView = findViewById<TextView>(R.id.addressLatitude)
        val longitudeTextView = findViewById<TextView>(R.id.addressLongitude)

        displayNameTextView.text = baseContext.getString(R.string.address_displayName_field, address.displayName)
        latitudeTextView.text = baseContext.getString(R.string.latitude_field, address.latitude)
        longitudeTextView.text = baseContext.getString(R.string.longitude_field, address.longitude)

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

    private fun updateLocation(location: Location?) {
        Log.i(TAG, "updateLocation")
        val positionLatitudeTextView = findViewById<TextView>(R.id.positionLatitude)
        val positionLongitudeTextView = findViewById<TextView>(R.id.positionLongitude)

        positionLatitudeTextView.text = baseContext.getString(R.string.latitude_field, location?.latitude)
        positionLongitudeTextView.text = baseContext.getString(R.string.longitude_field, location?.longitude)

        val result = FloatArray(1)
        Location.distanceBetween(address.latitude, address.longitude, location!!.latitude, location.longitude, result)

        val distanceTextView = findViewById<TextView>(R.id.distance)
        distanceTextView.text = baseContext.getString(R.string.distance, result[0].toInt())
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