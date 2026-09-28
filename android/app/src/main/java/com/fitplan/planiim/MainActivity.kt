package com.fitplan.planiim

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = uploadCallback ?: return@registerForActivityResult
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        cb.onReceiveValue(if (uri != null) arrayOf(uri) else null); uploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.allowFileAccess = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = object: WebChromeClient() {
            override fun onShowFileChooser(v: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                uploadCallback?.onReceiveValue(null); uploadCallback = cb
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
                filePicker.launch(i); return true
            }
        }
        web.addJavascriptInterface(FitPlanBridge(this), "FitPlanAndroid")
        if (savedInstanceState == null) web.loadUrl("https://fitplan-plani-im.netlify.app/") else web.restoreState(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) { override fun handleOnBackPressed(){ if(web.canGoBack()) web.goBack() else finish() } })
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }

    inner class FitPlanBridge(private val ctx: Context) {
        @JavascriptInterface fun isBluetoothAvailable(): Boolean = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter != null
        @JavascriptInterface fun isBluetoothEnabled(): Boolean = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
        @JavascriptInterface fun openBluetoothSettings() { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        @JavascriptInterface fun requestBluetoothPermissions() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 701)
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 702)
            }
        }
    }
}
