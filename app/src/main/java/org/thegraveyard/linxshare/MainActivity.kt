package org.thegraveyard.linxshare

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.BufferedSink
import kotlin.concurrent.thread
import okio.source
import okhttp3.Request
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.MimeTypeMap
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import android.widget.ArrayAdapter
import okhttp3.MultipartBody

class MainActivity : AppCompatActivity() {
    companion object {
        private const val LINX_SERVER_KEY = "linx_server"
        private const val DELETE_KEY_KEY = "delete_key"
        private const val API_KEY_KEY = "api_key"
        private const val EXPIRATION_KEY = "expiration"
        private const val RANDOMIZE_FILENAME_KEY = "randomize_filename"
    }

    @Serializable
    data class LinxResponseModel(
        val delete_key: String,
        val direct_url: String,
        val expiry: String,
        val filename: String,
        val mimetype: String,
        val sha256sum: String,
        val size: String,
        val url: String
    )

    data class Expiry(val expiration: Long, val label: String) {
        override fun toString(): String {
            return label
        }
    }

    private var compatibleIntent = false
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val expirations = ArrayList<Expiry>()
        expirations.add(Expiry(60, "1 minute"))
        expirations.add(Expiry(300, "5 minutes"))
        expirations.add(Expiry(3600, "1 hour"))
        expirations.add(Expiry(3600 * 24, "1 day"))
        expirations.add(Expiry(3600 * 24 * 7, "1 week"))
        expirations.add(Expiry(3600 * 24 * 7 * 4, "4 weeks"))
        expirations.add(Expiry(3600 * 24 * 365, "1 year"))
        expirations.add(Expiry(0, "never"))

        spinner_expiration.adapter = ArrayAdapter<Expiry>(
            applicationContext,
            android.R.layout.simple_spinner_dropdown_item,
            expirations
        )

        getPreferences(Context.MODE_PRIVATE).let { sharedPrefs ->
            val linxUrl = sharedPrefs.getString(LINX_SERVER_KEY, "https://")
            val deleteKey = sharedPrefs.getString(DELETE_KEY_KEY, "")
            val apiKey = sharedPrefs.getString(API_KEY_KEY, "")
            val expiration = sharedPrefs.getLong(EXPIRATION_KEY, 0L)
            val randomizeFilename = sharedPrefs.getBoolean(RANDOMIZE_FILENAME_KEY, true)
            et_linx_url.setText(linxUrl)
            et_delete_key.setText(deleteKey)
            et_api_key.setText(apiKey)
            spinner_expiration.setSelection(expirations.indexOfFirst { it.expiration == expiration })
            cb_randomize_filename.isChecked = randomizeFilename
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    compatibleIntent = true
                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                        uri.lastPathSegment?.let {
                            if (!it.contains('.')) {
                                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.type)
                                etFilename.setText("${it}.${ext}")
                            } else {
                                etFilename.setText(uri.lastPathSegment ?: "")
                            }
                        }
                    }
                }
            }
        }

        btn_upload.isEnabled = compatibleIntent
        btn_cancel.isEnabled = compatibleIntent
        etFilename.isEnabled = compatibleIntent && !cb_randomize_filename.isChecked

        cb_randomize_filename.setOnCheckedChangeListener { _, b ->
            etFilename.isEnabled = compatibleIntent && !b
        }

        btn_upload.setOnClickListener {
            val linxUrl = et_linx_url.text.toString()
            val deleteKey = et_delete_key.text.toString()
            val expiration = (spinner_expiration.selectedItem as Expiry).expiration
            val randomizeFilename = cb_randomize_filename.isChecked
            val filename = etFilename.text.toString()

            btn_upload.isEnabled = false
            btn_cancel.isEnabled = false
            handleSendImage(
                intent,
                linxUrl,
                deleteKey,
                expiration,
                randomizeFilename,
                filename
            )
        }
        btn_cancel.setOnClickListener {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()

        val linxUrl = et_linx_url.text.toString()
        val deleteKey = et_delete_key.text.toString()
        val apiKey = et_api_key.text.toString()
        val expiration = (spinner_expiration.selectedItem as Expiry).expiration
        val randomizeFilename = cb_randomize_filename.isChecked

        with (getPreferences(Context.MODE_PRIVATE).edit()) {
            putString(LINX_SERVER_KEY, linxUrl)
            putString(DELETE_KEY_KEY, deleteKey)
            putString(API_KEY_KEY, apiKey)
            putLong(EXPIRATION_KEY, expiration)
            putBoolean(RANDOMIZE_FILENAME_KEY, randomizeFilename)
            apply()
        }
    }

    private fun handleSendImage(
        intent: Intent,
        linxUrl: String,
        deleteKey: String,
        expiration: Long,
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
                    .url("${linxUrl}/upload/")
                    .addHeader("Accept", "application/json")
                    .addHeader("Linx-Delete-Key", deleteKey)
                    .addHeader("Linx-Expiry", expiration.toString())

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

                client.newCall(request).execute().use { response ->
                    response.body?.string()?.let { body ->
                        val json = Json(JsonConfiguration.Stable)
                        json.parse(LinxResponseModel.serializer(), body).let { lr ->
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("linx url", lr.url)
                            clipboard.setPrimaryClip(clip)
                            runOnUiThread {
                                val toast = Toast.makeText(applicationContext, "Copied ${lr.url} to clipboard", Toast.LENGTH_LONG)
                                toast.show()
                            }
                        }
                    }
                }
            }
        }
    }
}
