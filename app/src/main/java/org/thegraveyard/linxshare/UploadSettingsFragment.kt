package org.thegraveyard.linxshare

import android.os.Bundle
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat

class UploadSettingsFragment : PreferenceFragmentCompat() {
    class DataStore(private val parent: UploadSettingsFragment) : PreferenceDataStore() {
        private val data: MutableMap<String, String?> = mutableMapOf()

        override fun putString(key: String, value: String?) {
            data[key] = value
        }

        override fun getString(key: String, defValue: String?): String? {
            return data[key] ?: defValue
        }

        override fun putBoolean(key: String, value: Boolean) {
            data[key] = if (value) { "true" } else { "false" }
            if (key == "randomize_filename") {
                parent.onRandomizeFilenameChanged(value)
            }
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return data[key]?.let { it == "true" } ?: defValue
        }
    }

    private val dataStore = DataStore(this)

    var deleteKey: String
        get() = dataStore.getString("delete_key", "") ?: ""
        set(value) = dataStore.putString("delete_key", value)

    var expiration: Int
        get() = (dataStore.getString("expiration", "0") ?: "0").toInt()
        set(value) = dataStore.putString("expiration", value.toString())

    var randomizeFilename: Boolean
        get() = dataStore.getBoolean("randomize_filename", true)
        set(value) = dataStore.putBoolean("randomize_filename", value)

    var filename: String
        get() = dataStore.getString("filename", "") ?: ""
        set(value) = dataStore.putString("filename", value)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.upload_preferences, rootKey)

        val sharedPreferences = preferenceManager.sharedPreferences
        deleteKey = sharedPreferences.getString("delete_key", "") ?: ""
        expiration = (sharedPreferences.getString("expiration", "0") ?: "0").toInt()
        randomizeFilename = sharedPreferences.getBoolean("randomize_filename", true)

        findPreference("delete_key").preferenceDataStore = dataStore
        findPreference("expiration").preferenceDataStore = dataStore
        findPreference("randomize_filename").preferenceDataStore = dataStore
        findPreference("filename").preferenceDataStore = dataStore

        onRandomizeFilenameChanged(randomizeFilename)
    }

    fun onRandomizeFilenameChanged(value: Boolean) {
        findPreference("filename").isVisible = !value
    }
}
