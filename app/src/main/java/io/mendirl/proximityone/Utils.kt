package io.mendirl.proximityone

import android.content.Context
import android.location.Location
import android.preference.PreferenceManager
import java.text.DateFormat
import java.util.*

object Utils {

    private const val KEY_REQUESTING_LOCATION_UPDATES = "requesting_location_updates"

    fun locationText(location: Location): String {
        return "(${location.latitude}, ${location.longitude})"
    }

    fun locationTitle(context: Context): String {
        return context.getString(R.string.location_updated, DateFormat.getDateTimeInstance().format(Date()))
    }


    fun requestingLocationUpdates(context: Context?): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_REQUESTING_LOCATION_UPDATES, false)
    }

    /**
     * Stores the location updates state in SharedPreferences.
     * @param requestingLocationUpdates The location updates state.
     */
    fun requestingLocationUpdates(context: Context?, requestingLocationUpdates: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(KEY_REQUESTING_LOCATION_UPDATES, requestingLocationUpdates).apply()
    }
}