package org.thegraveyard.linxshare

import android.os.Bundle
import androidx.preference.*

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

        findPreference<EditTextPreference>("delete_key")?.let {
            it.preferenceDataStore = dataStore
            it.text = deleteKey

            it.setSummaryProvider { preference ->
                if (preferenceManager.sharedPreferences.getString(preference.key, "").isNullOrEmpty()) {
                    getString(R.string.no_delete_key_set)
                } else {
                    getString(R.string.delete_key_set)
                }
            }
        }

        findPreference<ListPreference>("expiration")?.let {
            it.preferenceDataStore = dataStore
            it.value = expiration.toString()

            it.setSummaryProvider { preference ->
                val value = preferenceManager.sharedPreferences.getString(preference.key, "0")!!
                it.entries[it.findIndexOfValue(value)]
            }
        }

        findPreference<SwitchPreferenceCompat>("randomize_filename")?.let {
            it.preferenceDataStore = dataStore
            it.isChecked = randomizeFilename
        }

        findPreference<EditTextPreference>("filename")?.let {
            it.preferenceDataStore = dataStore
            it.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            it.text = filename
        }

        onRandomizeFilenameChanged(randomizeFilename)
    }

    fun onRandomizeFilenameChanged(value: Boolean) {
        findPreference<Preference>("filename")?.isVisible = !value
    }
}
