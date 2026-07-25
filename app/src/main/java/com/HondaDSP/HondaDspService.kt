package com.HondaDSP

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class HondaDspService : Service() {

    companion object {
        var ins2: HondaDspService? = null
        fun getInstance(): HondaDspService? {
            return ins2
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var isDone :Boolean by Delegates.observable(false){ property, oldValue, newValue ->
        if(newValue)
        {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }




    
    private val hex00a : UByte = 19.toUByte()
    private val hex01a : UByte = 14.toUByte()
    private val hex02a : UByte = 1.toUByte()
    private val hex03a : UByte = 32.toUByte()
    private var hex04a : UByte = 0u
    private var hex05a : UByte = 0u
    private var hex06a : UByte = 0u
    private var hex07a : UByte = 0u
    private var hex08a : UByte = 0u
    private var hex09a : UByte = 0u
    private var hex09aApp : UByte = 0u
    private var hex09aSys : UByte = 0u
    private var hex10aa : String = "0"
    private var hex10ab : String = "0"
    private var hex10a : UByte = 0u
    private val hex11a : UByte = 0.toUByte()
    private val hex12a : UByte = 0.toUByte()
    private val hex13a : UByte = 0.toUByte()
    private val hex14a : UByte = 0.toUByte()

    private var svc : String = "Off"
    private var volSys : Int = 10
    private var volApp : Int = 10

    private var gpsSource : String = "sys"
    private var volSource : String = "sys"

    private var ampErrors : Int = 0
    private var reconnectingUsb = false
    
    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null
    val ACTION_USB_PERMISSION = "permission"

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action!! == ACTION_USB_PERMISSION) {
                val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    m_connection = m_usbManager.openDevice(m_device)
                    m_serial = UsbSerialDevice.createUsbSerialDevice(m_device, m_connection)
                    if (m_serial != null) {
                        if (m_serial!!.open()) {
                            m_serial!!.setBaudRate(9600)
                            m_serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                            m_serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                            m_serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                            m_serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                            m_serial!!.read(mCallback)
                            sendCurrentPacketAsync()
                        } else {
                            Log.i("Serial", "port not open")
                            Handler(Looper.getMainLooper()).post {
                                val toast = Toast.makeText(
                                    applicationContext, "port not open",
                                    Toast.LENGTH_SHORT
                                )
                                toast.show()
                            }

                        }
                    } else {
                        Log.i("Serial", "port is null")
                        Handler(Looper.getMainLooper()).post {
                            val toast = Toast.makeText(
                                applicationContext, "port is null",
                                Toast.LENGTH_SHORT
                            )
                            toast.show()
                        }
                    }
                } else {
                    Log.i("Serial","permission not granted")
                    Handler(Looper.getMainLooper()).post {
                        val toast = Toast.makeText(
                            applicationContext, "permission not granted",
                            Toast.LENGTH_SHORT
                        )
                        toast.show()
                    }
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                startUsbConnecting()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                disconnect()
            }
        }
    }

    private var volReceiver: BroadcastReceiver? = null

    private lateinit var mCallback: UsbSerialInterface.UsbReadCallback

    
    
    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        log("The service has been created".uppercase())
        val notification = createNotification()
        startForeground(1, notification)



    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        unregisterReceiver(volReceiver)
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        disconnect()

        log("The service has been destroyed".uppercase())
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, HondaDspService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent: PendingIntent = PendingIntent.getService(this, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT)
        applicationContext.getSystemService(Context.ALARM_SERVICE)
        val alarmService: AlarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
    }

    
    
    
    private fun startUsbConnecting() {
        val usbDevices: HashMap<String, UsbDevice>? = m_usbManager.deviceList
        if (!usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach{ entry ->
                m_device = entry.value
                val deviceVendorId: Int? = m_device?.vendorId
                Log.i("serial", "vendorId: "+deviceVendorId)

                if (deviceVendorId == 1027) {
                    val intent: PendingIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION),0)
                    m_usbManager.requestPermission(m_device, intent)
                    reconnectingUsb = false
                    keep = false
                    Log.i("serial", "connection successful")
                    Handler(Looper.getMainLooper()).post {
                        val toast = Toast.makeText(
                            applicationContext, "connection successful",
                            Toast.LENGTH_SHORT
                        )
                        toast.show()
                    }
                } else {
                    m_connection = null
                    m_device = null
                    Log.i("serial", "unable to connect")
                    Handler(Looper.getMainLooper()).post {
                        val toast = Toast.makeText(applicationContext, "unable to connect",
                            Toast.LENGTH_SHORT
                        )
                        toast.show()
                    }


                }
                if (!keep) {
                    return
                }
            }
        } else {
            Log.i("serial", "no usb device connected")

        }
    }

    private fun sendData(input: ByteArray) {
        m_serial?.write(input)
        Log.i("serial", "sending data: "+input)
    }

    private fun disconnect() {
        m_serial?.close()
        m_serial = null
        m_connection?.close()
        m_connection = null
        m_device = null
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "")
    { eachByte -> "%02x".format(eachByte) }
    
    private fun configureVolumeReceiver() {
        val filter = IntentFilter()
        filter.addAction("android.media.VOLUME_CHANGED_ACTION")
        volReceiver = VolReceiver()
        registerReceiver(volReceiver, filter)
    }

    private fun sendCurrentPacketAsync() {
        if (!isServiceStarted || m_serial == null) return
        GlobalScope.launch(Dispatchers.IO) {
            policzService()
        }
    }

    private fun reconnectUsb() {
        if (!isServiceStarted || reconnectingUsb) return
        reconnectingUsb = true
        disconnect()
        GlobalScope.launch(Dispatchers.IO) {
            delay(500)
            startUsbConnecting()
        }
    }
    
    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        }
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)
        restoreHex()

        ins2 = this
        m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        configureVolumeReceiver()

        mCallback = object : UsbSerialInterface.UsbReadCallback {
            override fun onReceivedData(arg0: ByteArray?) {
                try {
                    var data: String? = null
                    try {
                        //data = String(arg0 as ByteArray, Charset.forName("UTF-8"))
                        if (arg0 != null) {
                            data = arg0.toHex()

                        }
                        "$data/n"
                        MainActivity.getInstance()?.tvAppend(data.toString())
                        if (data == "14806c") {
                            MainActivity.getInstance()?.updateAns("ok")
                            ampErrors = 0
                        }
                        else{
                            MainActivity.getInstance()?.updateAns("notok")
                        }
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(broadcastReceiver, filter)
        startUsbConnecting()



        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                //locationResult
                if (!isDone) {
                    val speedToInt = (locationResult.lastLocation.speed / 0.28).roundToInt()
                    hex09aSys = speedToInt.toUByte()

                }
            }
        }
        startLocationUpdates()




        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HondaDspService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {

                    policzService()
                    ampErrors += 1
                    MainActivity.getInstance()?.updateAmpErrors(ampErrors)
                    MainActivity.getInstance()?.updateGps(hex09aSys.toInt(),"sys")
                    if (ampErrors == 10) {
                        ampErrors = 0
                        log("No amplifier response, reconnecting USB serial")
                        reconnectUsb()
                    }

                }
                delay(1 * 1000)
            }
            log("End of the loop for the service sendData")
        }

    }

    private fun stopService() {
        log("Stopping the foreground service")
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        }
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "Honda DSP SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Honda DSP notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Honda DSP Service"
                it.enableLights(true)
                it.lightColor = Color.RED
                //it.enableVibration(true)
                //it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        //}

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder: Notification.Builder = //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            Notification.Builder(
            this,
            notificationChannelId
        ) //else Notification.Builder(this)

        //val mainActivity = MainActivity()

        return builder
            .setContentTitle("Honda Accord DSP")
            .setContentText("Volume and speed monitor")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setTicker("Ticker text")

            //.setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }

    fun updateHex(hexName: String, t: Int) {
        Handler(Looper.getMainLooper()).post {
            //var vol3 = t
            when (hexName) {
                "volSysValue" -> volSys = t
                "volAppValue" -> volApp = t
                "center" -> hex10aa = t.toString()
                "sub" -> hex10ab = t.toString()
                "bas" -> hex05a = t.toUByte()
                "treble" -> hex06a = t.toUByte()
                "bal" -> hex07a = t.toUByte()
                "fader" -> hex08a = t.toUByte()
                "speedApp" -> hex09aApp = t.toUByte()
                "volApp" -> volSource = t.toString()
                "gps" -> gpsSource = t.toString()
                "svc" -> svc = t.toString()

            }
            //policzService()
        }
    }

    fun updateHexString(hexName: String, t: String) {
        Handler(Looper.getMainLooper()).post {
            //var vol3 = t
            when (hexName) {
                "center" -> hex10aa = t
                "sub" -> hex10ab = t
                "volApp" -> volSource = t
                "gps" -> gpsSource = t
                "svc" -> svc = t

            }
            //policzService()
        }
    }

    fun policzService() {

        val sharedPreference = getSharedPreferences("Pakiet",Context.MODE_PRIVATE)

        hex10a = (hex10aa + hex10ab).toInt(16).toUByte()

        var vol3 = 0
        if (volSource == "sys"){
            vol3 = volSys
        }
        if (volSource == "app"){
            vol3 = volApp
        }

        hex04a = vol3.toUByte()
        if (gpsSource == "sys"){
            hex09a = hex09aSys
        }
        if (gpsSource == "app"){
            hex09a = hex09aApp
        }



        val suma = (hex00a.toInt() + hex01a.toInt() + hex02a.toInt() + hex03a.toInt() + hex04a.toInt() + hex05a.toInt() + hex06a.toInt() +  hex07a.toInt() + hex08a.toInt() + hex09a.toInt() + hex10a.toInt() + hex11a.toInt() + hex12a.toInt() + hex13a.toInt() + hex14a.toInt()).toString(16)
        val ostatnieSumy = suma.takeLast(2).toInt(16)
        val dopelnienie = (256 - ostatnieSumy).toString(16)
        val hex15a = dopelnienie.takeLast(2).toInt(16).toUByte()


        val pakietArray2 = byteArrayOf(
            hex00a.toByte(),
            hex01a.toByte(),
            hex02a.toByte(),
            hex03a.toByte(),
            hex04a.toByte(),
            hex05a.toByte(),
            hex06a.toByte(),
            hex07a.toByte(),
            hex08a.toByte(),
            hex09a.toByte(),
            hex10a.toByte(),
            hex11a.toByte(),
            hex12a.toByte(),
            hex13a.toByte(),
            hex14a.toByte(),
            hex15a.toByte()
        )


        val crc = (hex00a.toInt() + hex01a.toInt() + hex02a.toInt() + hex03a.toInt() + hex04a.toInt() + hex05a.toInt() + hex06a.toInt() +  hex07a.toInt() + hex08a.toInt() + hex09a.toInt() + hex10a.toInt() + hex11a.toInt() + hex12a.toInt() + hex13a.toInt() + hex14a.toInt() + hex15a.toInt()).toString(16).takeLast(2)

        if (crc == "00") {
            MainActivity.getInstance()?.updateCrc("ok")
        }
        else{
            MainActivity.getInstance()?.updateCrc("notok")
        }

        fun ByteArray.toHex(): String = joinToString(separator = "")
        { eachByte -> "%02x".format(eachByte) }

        val pakietTxt = pakietArray2.toHex()


        val editor = sharedPreference.edit()
        editor.putString("Pakiet_zap", pakietArray2.toHex())
        editor.apply()


        sendData(pakietArray2)
        TimeUnit.MILLISECONDS.sleep(100L)
        log("fn Policz - wyslano: " + pakietTxt + ", crc: " + crc)

    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun restoreHex() {
        val sharedPreference = getSharedPreferences("Pakiet", Context.MODE_PRIVATE)
        val pakiet2 = sharedPreference.getString("Pakiet_zap", "00")
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val savedAppVolume = sharedPreference.getInt("VolumeAppValue", currentVolume)

        svc = "Off"
        volSys = currentVolume
        volApp = savedAppVolume
        gpsSource = sharedPreference.getString("speedGPS", "sys").toString()
        volSource = sharedPreference.getString("volApp", "sys").toString()

        if (pakiet2?.length == 32) {

            hex05a = (pakiet2.subSequence(10..11).toString().toIntOrNull(16) ?: 0).toUByte()
            hex06a = (pakiet2.subSequence(12..13).toString().toIntOrNull(16) ?: 0).toUByte()
            hex07a = (pakiet2.subSequence(14..15).toString().toIntOrNull(16) ?: 0).toUByte()
            hex08a = (pakiet2.subSequence(15..17).toString().toIntOrNull(16) ?: 0).toUByte()
            hex10aa = (pakiet2[20].toString())
            hex10ab = (pakiet2[21].toString())
        }
    }

}
