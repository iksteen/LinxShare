package org.thegraveyard.linxshare

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import okio.source
import kotlin.concurrent.thread

class UploadActivity : AppCompatActivity() {
    @Serializable
    data class LinxResponseModel(
        val url: String
    )

    private var compatibleIntent = false
    private val client = OkHttpClient()

    private lateinit var optionsMenu: Menu

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upload)

        val uploadSettingsFragment: UploadSettingsFragment = if (savedInstanceState == null) {
            val fragment = UploadSettingsFragment()
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.upload_settings_container, fragment, "UPLOAD_SETTINGS")
                .commit()
            fragment
        } else {
            supportFragmentManager.findFragmentByTag("UPLOAD_SETTINGS") as UploadSettingsFragment
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    compatibleIntent = true

                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                        uri.lastPathSegment?.let {
                            if (!it.contains('.')) {
                                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.type)
                                uploadSettingsFragment.filename = "$it.$ext"
                            } else {
                                uploadSettingsFragment.filename = uri.lastPathSegment ?: ""
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        optionsMenu = menu
        menuInflater.inflate(R.menu.appbar, menu)
        menu.findItem(R.id.upload).isEnabled = compatibleIntent
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val uploadSettings = supportFragmentManager.findFragmentByTag("UPLOAD_SETTINGS") as UploadSettingsFragment

        if (item.itemId == R.id.upload) {
            item.isEnabled = false

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val linxUrl = sharedPreferences.getString("linx_url", "") ?: ""
            val apiKey = sharedPreferences.getString("api_key", "") ?: ""
            val deleteKey = uploadSettings.deleteKey
            val expiration = uploadSettings.expiration
            val randomizeFilename = uploadSettings.randomizeFilename
            val filename = uploadSettings.filename

            handleSendImage(
                intent,
                linxUrl,
                deleteKey,
                apiKey,
                expiration,
                randomizeFilename,
                filename
            )
        }
        return super.onOptionsItemSelected(item)
    }

    private fun handleSendImage(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        apiKey: String,
        expiration: Int,
        randomizeFilename: Boolean,
        filename: String
    ) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
            thread(start = true) {
                val body = object : RequestBody() {
                    override fun contentType(): MediaType? {
                        return intent.type?.toMediaType()
                    }

                    override fun contentLength(): Long {
                        return -1
                    }

                    override fun writeTo(sink: BufferedSink) {
                        contentResolver.openInputStream(uri)?.source()?.use {
                            sink.writeAll(it)
                        }
                    }
                }

                val builder = Request.Builder()
                    .url("${linxUrl.trimEnd('/')}/upload/")
                    .addHeader("Accept", "application/json")
                    .addHeader("Linx-Delete-Key", deleteKey)
                    .addHeader("Linx-Expiry", expiration.toString())
                    .addHeader("Linx-Api-Key", apiKey)

                val request: Request = if (randomizeFilename) {
                    builder
                        .addHeader("Linx-Randomize", "yes")
                        .put(body)
                        .build()
                } else {
                    val formBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", filename, body)
                        .build()
                    builder
                        .post(formBody)
                        .build()
                }

                try {
                    client.newCall(request).execute().use { response ->
                        handleResponse(response)
                    }
                }
                catch (e: Exception) {
                    Log.e("MainActivity", "Request failed: ${e}")
                    handleFailure()
                }
            }
        }
    }

    private fun handleResponse(response: Response) {
        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            val json = Json(JsonConfiguration.Stable.copy(strictMode=false))
            val lr = json.parse(LinxResponseModel.serializer(), body)
            handleSuccess(lr.url)
        } else {
            handleFailure()
        }
    }

    private fun handleSuccess(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("linx url", url)
        clipboard.setPrimaryClip(clip)

        runOnUiThread {
            val toast = Toast.makeText(applicationContext, "Copied ${url} to clipboard", Toast.LENGTH_SHORT)
            toast.show()
            finish()
        }
    }

    private fun handleFailure() {
        runOnUiThread {
            val toast = Toast.makeText(
                applicationContext,
                "Failed to upload to linx-server, check settings!",
                Toast.LENGTH_LONG
            )
            toast.show()

            optionsMenu.findItem(R.id.upload).isEnabled = true
        }
    }
}
