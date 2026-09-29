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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var health: HealthConnectClient
    private val healthPermissions = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(DistanceRecord::class), HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class), HealthPermission.getReadPermission(HeartRateRecord::class), HealthPermission.getReadPermission(WeightRecord::class))
    private val healthPermissionLauncher = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted -> if (::web.isInitialized) web.evaluateJavascript("window.fitPlanHealthPermissionResult && window.fitPlanHealthPermissionResult("+granted.containsAll(healthPermissions)+")", null) }
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = uploadCallback ?: return@registerForActivityResult
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        cb.onReceiveValue(if (uri != null) arrayOf(uri) else null); uploadCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) health = HealthConnectClient.getOrCreate(this)
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
        @JavascriptInterface fun requestHealthPermissions() { runOnUiThread { if (::health.isInitialized) CoroutineScope(Dispatchers.Main).launch { val granted=health.permissionController.getGrantedPermissions(); if(granted.containsAll(healthPermissions)) web.evaluateJavascript("window.fitPlanHealthPermissionResult && window.fitPlanHealthPermissionResult(true)",null) else healthPermissionLauncher.launch(healthPermissions) } } }
        @JavascriptInterface fun readTodayHealth() { if(!::health.isInitialized){ runOnUiThread{ web.evaluateJavascript("window.fitPlanHealthData && window.fitPlanHealthData({error:'Health Connect unavailable'})",null)}; return }; CoroutineScope(Dispatchers.IO).launch { try { val zone=ZoneId.systemDefault(); val start=ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant(); val end=Instant.now(); val agg=health.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL,DistanceRecord.DISTANCE_TOTAL,ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),TimeRangeFilter.between(start,end))); val hrs=health.readRecords(androidx.health.connect.client.request.ReadRecordsRequest(HeartRateRecord::class,TimeRangeFilter.between(start,end))); val bpm=hrs.records.flatMap{it.samples}.lastOrNull()?.beatsPerMinute ?: 0L; val steps=agg[StepsRecord.COUNT_TOTAL] ?: 0L; val km=(agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0); val kcal=(agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0); val json="{\\\"steps\\\":$steps,\\\"distance_km\\\":$km,\\\"calories\\\":$kcal,\\\"heart_rate\\\":$bpm}"; withContext(Dispatchers.Main){web.evaluateJavascript("window.fitPlanHealthData && window.fitPlanHealthData($json)",null)} } catch(e:Exception){ val msg=(e.message?:"error").replace("'",""); withContext(Dispatchers.Main){web.evaluateJavascript("window.fitPlanHealthData && window.fitPlanHealthData({error:'$msg'})",null)} } } }
        @JavascriptInterface fun requestBluetoothPermissions() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 701)
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 702)
            }
        }
    }
}
