package com.fitplan.planiim

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import java.util.UUID
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
    private var scaleGatt: BluetoothGatt? = null
    private var scaleScan: ScanCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastScaleWeight = 0.0
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

    private val scaleGattCallback=object:BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt:BluetoothGatt,status:Int,newState:Int){
            if(newState==BluetoothProfile.STATE_CONNECTED){scaleGatt=gatt;jsFromActivity("window.fitPlanScaleStatus && window.fitPlanScaleStatus('SENSSUN u lidh. Po lexoj shërbimet…')");try{gatt.discoverServices()}catch(_:Exception){}}
            else if(newState==BluetoothProfile.STATE_DISCONNECTED){jsFromActivity("window.fitPlanScaleStatus && window.fitPlanScaleStatus('Peshorja u shkëput.')")}
        }
        override fun onServicesDiscovered(gatt:BluetoothGatt,status:Int){
            var count=0
            gatt.services.forEach{svc->svc.characteristics.forEach{ch->
                val props=ch.properties
                if((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0 || (props and BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0){
                    try{
                        if(gatt.setCharacteristicNotification(ch,true)){
                            val uuid=if((props and BluetoothGattCharacteristic.PROPERTY_INDICATE)!=0) UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") else UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            ch.getDescriptor(uuid)?.let{d->d.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;gatt.writeDescriptor(d)}
                            count++
                        }
                    }catch(_:Exception){}
                }
            }}
            jsFromActivity("window.fitPlanScaleStatus && window.fitPlanScaleStatus('SENSSUN u lidh — prit matjen në peshore.')")
        }
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt:BluetoothGatt,ch:BluetoothGattCharacteristic){parseScaleBytes(ch.value)}
        override fun onCharacteristicChanged(gatt:BluetoothGatt,ch:BluetoothGattCharacteristic,value:ByteArray){parseScaleBytes(value)}
    }
    private fun jsFromActivity(code:String){runOnUiThread{if(::web.isInitialized)web.evaluateJavascript(code,null)}}
    private fun parseScaleBytes(bytes:ByteArray){
        if(bytes.size<2)return
        val candidates=mutableListOf<Double>()
        for(i in 0 until bytes.size-1){
            val a=bytes[i].toInt() and 255; val b=bytes[i+1].toInt() and 255
            val be=(a shl 8) or b; val le=a or (b shl 8)
            listOf(be/10.0,be/100.0,le/10.0,le/100.0).forEach{if(it in 20.0..250.0)candidates.add(it)}
        }
        if(candidates.isEmpty())return
        val ref=if(lastScaleWeight>0)lastScaleWeight else 70.0
        val w=candidates.minByOrNull{kotlin.math.abs(it-ref)}?:return
        lastScaleWeight=w
        jsFromActivity("window.fitPlanScaleWeight && window.fitPlanScaleWeight("+String.format(java.util.Locale.US,"%.1f",w)+")")
    }

    inner class FitPlanBridge(private val ctx: Context) {
        private fun jsScaleStatus(msg:String){ runOnUiThread { web.evaluateJavascript("window.fitPlanScaleStatus && window.fitPlanScaleStatus("+org.json.JSONObject.quote(msg)+")",null) } }
        private fun jsScaleWeight(w:Double){ runOnUiThread { web.evaluateJavascript("window.fitPlanScaleWeight && window.fitPlanScaleWeight($w)",null) } }
        @JavascriptInterface fun connectScale(){
            runOnUiThread {
                val manager=ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter=manager.adapter
                if(adapter==null){jsScaleStatus("Bluetooth nuk gjendet në këtë telefon.");return@runOnUiThread}
                if(!adapter.isEnabled){jsScaleStatus("Aktivizo Bluetooth-in dhe provo përsëri.");startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS));return@runOnUiThread}
                if(Build.VERSION.SDK_INT>=31 && ContextCompat.checkSelfPermission(ctx,Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT),701);jsScaleStatus("Jep lejen Bluetooth dhe shtyp Lidhu përsëri.");return@runOnUiThread}
                jsScaleStatus("Po kërkoj SENSSUN… ndiz peshoren duke hipur mbi të.")
                val scanner=adapter.bluetoothLeScanner ?: run {jsScaleStatus("BLE scanner nuk është i disponueshëm.");return@runOnUiThread}
                scaleScan?.let{scanner.stopScan(it)}
                val cb=object:ScanCallback(){
                    override fun onScanResult(type:Int,result:ScanResult){
                        val name=try{result.device.name?:""}catch(e:SecurityException){""}
                        val advertised=result.scanRecord?.deviceName?:""
                        val n=(name+" "+advertised).lowercase()
                        if(n.contains("senssun")||n.contains("if1031")||n.contains("if103")){
                            try{scanner.stopScan(this)}catch(_:Exception){}
                            jsScaleStatus("U gjet "+(name.ifBlank{advertised.ifBlank{"SENSSUN"}})+". Po lidhem…")
                            try{scaleGatt=result.device.connectGatt(ctx,false,scaleGattCallback)}catch(e:Exception){jsScaleStatus("Lidhja dështoi: "+(e.message?:"gabim"))}
                        }
                    }
                    override fun onScanFailed(code:Int){jsScaleStatus("Kërkimi BLE dështoi ($code).")}
                }
                scaleScan=cb; scanner.startScan(cb)
                mainHandler.postDelayed({try{scanner.stopScan(cb)}catch(_:Exception){}; if(scaleGatt==null)jsScaleStatus("SENSSUN nuk u gjet. Hip mbi peshore dhe provo përsëri.")},15000)
            }
        }
        @JavascriptInterface fun disconnectScale(){try{scaleGatt?.disconnect();scaleGatt?.close()}catch(_:Exception){};scaleGatt=null;jsScaleStatus("Peshorja u shkëput.")}
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
