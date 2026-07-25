package com.HondaDSP

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.location.Location
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import java.util.HashMap


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {

    companion object {
        var ins: MainActivity? = null
        fun getInstance(): MainActivity? {
            return ins
        }
    }

    //lateinit var m_usbManager: UsbManager

    //val ACTION_USB_PERMISSION = "permission"

    private val LOCATION_PERM = 124

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    private var volAppSource = "sys"
    private var gpsAppSource = "sys"

    private lateinit var sharedPreference : SharedPreferences
    private lateinit var editor : SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log(this, "MainActivity onCreate intent=${intent?.action}")

        setContentView(R.layout.activity_main)

        sharedPreference =  getSharedPreferences("Pakiet", MODE_PRIVATE)
        editor = sharedPreference.edit()

        //m_usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        //startUsbConnecting()

        //val filter = IntentFilter()
        //filter.addAction(ACTION_USB_PERMISSION)
        //filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        //filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        //registerReceiver(broadcastReceiver, filter)

        //warstosci odczytanie


        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val vol : Int = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        val savedAppVolume = sharedPreference.getInt("VolumeAppValue", vol)
        val isSystemVolume = sharedPreference.getBoolean("volAppSwitch", true)
        volAppSource = if (isSystemVolume) "sys" else "app"
        vol2.text = if (isSystemVolume) vol.toString() else savedAppVolume.toString()

        //val editor = sharedPreference.edit()
        editor.putInt("vol", vol)
        editor.apply()

        seekBasy.progress = sharedPreference.getInt("Basy",3)
        seekTreble.progress = sharedPreference.getInt("Treble",2)
        seekCenter.progress = sharedPreference.getInt("Center",3)
        seekSub.progress = sharedPreference.getInt("Sub",2)
        seekBalans.progress = sharedPreference.getInt("Balans",-1)
        seekFader.progress = sharedPreference.getInt("Fader",0)
        volumeSeekbar.progress = savedAppVolume
        speedBar.progress = sharedPreference.getInt("speedBar",0)

        editor.putInt("SVC", R.id.SvcOff)
        editor.putString("SVC2", getString(R.string.off))
        editor.apply()

        radioGroup.check(R.id.SvcOff)
        progressBasy.text = sharedPreference.getInt("Basy",3).toString()
        progressTreble.text = sharedPreference.getInt("Treble",2).toString()
        progressCenter.text = sharedPreference.getInt("Center",3).toString()
        progressSub.text = sharedPreference.getInt("Sub",2).toString()
        Balans.text = sharedPreference.getInt("Balans",-1).toString()
        Fader.text = sharedPreference.getInt("Fader",0).toString()
        svcTx.text = getString(R.string.off)
        for (index in 0 until radioGroup.childCount) {
            radioGroup.getChildAt(index).isEnabled = false
        }

        volApp.isChecked = isSystemVolume
        speedGPS.isChecked = sharedPreference.getBoolean("gpsAppSwitch",true)

        imageAns.setImageResource(R.drawable.red_dot)
        imageCrc.setImageResource(R.drawable.red_dot)


        ins = this

        title = "Honda DSP Service"

        supportActionBar?.hide()

        //m_usbManager = getSystemService(USB_SERVICE) as UsbManager

        //startUsbConnecting()

        //GPS
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        askForLocationPermission()
        createLocationRequest()

        //service start
        actionOnService(Actions.START)
        //endless service

        findViewById<Button>(R.id.btnStartService).let {
            it.setOnClickListener {
                log("START THE FOREGROUND SERVICE ON DEMAND")
                actionOnService(Actions.START)
            }
        }

        findViewById<Button>(R.id.btnStopService).let {
            it.setOnClickListener {
                log("STOP THE FOREGROUND SERVICE ON DEMAND")
                actionOnService(Actions.STOP)
            }
        }

        //koniec endless service

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val radio_zaz: RadioButton = findViewById(checkedId)
            val svcChecked = radioGroup.checkedRadioButtonId


            //val editor = sharedPreference.edit()
            editor.putInt("SVC", svcChecked)
            editor.putString("SVC2", radio_zaz.text.toString())
            editor.apply()
            svcTx.text = radio_zaz.text
            HondaDspService.getInstance()?.updateHexString("svc", svcTx.text.toString())


        }


        volApp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                //val editor = sharedPreference.edit()
                editor.putString("volApp", "sys")
                editor.putBoolean("volAppSwitch", true)
                editor.apply()
                volAppSource = "sys"
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                updateVolume(currentVolume, "sys")
                HondaDspService.getInstance()?.updateHexString("volApp", "sys")
                val toast = Toast.makeText(applicationContext, "System Volume On", LENGTH_SHORT)
                toast.show()
            } else {
                //val editor = sharedPreference.edit()
                editor.putString("volApp", "app")
                editor.putBoolean("volAppSwitch", false)
                editor.apply()
                volAppSource = "app"

                vol2.text = sharedPreference.getInt("VolumeAppValue",15).toString()

                HondaDspService.getInstance()?.updateHexString("volApp", "app")
                val toast = Toast.makeText(applicationContext, "App Volume On", LENGTH_SHORT)
                toast.show()
            }
        }

        speedGPS.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                //val editor = sharedPreference.edit()
                editor.putString("speedGPS", "sys")
                editor.putBoolean("gpsAppSwitch", true)
                editor.apply()
                gpsAppSource = "sys"
                HondaDspService.getInstance()?.updateHexString("gps", "sys")
                val toast = Toast.makeText(applicationContext, "Speed form GPS", LENGTH_SHORT)
                toast.show()
            } else {
                //val editor = sharedPreference.edit()
                editor.putString("speedGPS", "app")
                editor.putBoolean("gpsAppSwitch", false)
                editor.apply()
                gpsAppSource = "app"
                currentSpeedID.text = sharedPreference.getInt("SpeedBar",0).toString()
                HondaDspService.getInstance()?.updateHexString("gps", "app")
                val toast = Toast.makeText(applicationContext, "Test Speed", LENGTH_SHORT)
                toast.show()
            }
        }

        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                //var progressVolume = progress

                //val editor = sharedPreference.edit()
                editor.putInt("SpeedBar", progress)
                editor.apply()
                HondaDspService.getInstance()?.updateHex("speedApp", progress)
                HondaDspService.getInstance()?.policzService()
                updateGps(progress,"app")

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        volumeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                //var progressVolume = progress

                //val editor = sharedPreference.edit()
                editor.putInt("VolumeAppValue", progress)
                editor.apply()
                HondaDspService.getInstance()?.updateHex("volAppValue", progress)
                HondaDspService.getInstance()?.policzService()
                updateVolume(progress,"app")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekBasy.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                progressBasy.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Basy", progress)
                editor.apply()
                when (progress) {
                    -6 -> HondaDspService.getInstance()?.updateHex("bas", 0)
                    -5 -> HondaDspService.getInstance()?.updateHex("bas", 21)
                    -4 -> HondaDspService.getInstance()?.updateHex("bas", 42)
                    -3 -> HondaDspService.getInstance()?.updateHex("bas", 63)
                    -2 -> HondaDspService.getInstance()?.updateHex("bas", 85)
                    -1 -> HondaDspService.getInstance()?.updateHex("bas", 106)
                    -0 -> HondaDspService.getInstance()?.updateHex("bas", 128)
                    1 -> HondaDspService.getInstance()?.updateHex("bas", 148)
                    2 -> HondaDspService.getInstance()?.updateHex("bas", 170)
                    3 -> HondaDspService.getInstance()?.updateHex("bas", 191)
                    4 -> HondaDspService.getInstance()?.updateHex("bas", 212)
                    5 -> HondaDspService.getInstance()?.updateHex("bas", 233)
                    6 -> HondaDspService.getInstance()?.updateHex("bas", 255)
                }
                HondaDspService.getInstance()?.policzService()

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekTreble.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                progressTreble.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Treble", progress)
                editor.apply()
                when (progress) {
                    -6 -> HondaDspService.getInstance()?.updateHex("treble", 0)
                    -5 -> HondaDspService.getInstance()?.updateHex("treble", 21)
                    -4 -> HondaDspService.getInstance()?.updateHex("treble", 42)
                    -3 -> HondaDspService.getInstance()?.updateHex("treble", 63)
                    -2 -> HondaDspService.getInstance()?.updateHex("treble", 85)
                    -1 -> HondaDspService.getInstance()?.updateHex("treble", 106)
                    -0 -> HondaDspService.getInstance()?.updateHex("treble", 128)
                    1 -> HondaDspService.getInstance()?.updateHex("treble", 148)
                    2 -> HondaDspService.getInstance()?.updateHex("treble", 170)
                    3 -> HondaDspService.getInstance()?.updateHex("treble", 191)
                    4 -> HondaDspService.getInstance()?.updateHex("treble", 212)
                    5 -> HondaDspService.getInstance()?.updateHex("treble", 233)
                    6 -> HondaDspService.getInstance()?.updateHex("treble", 255)
                }
                HondaDspService.getInstance()?.policzService()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekCenter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                progressCenter.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Center", progress)
                editor.apply()
                when (progress) {
                    -6 -> HondaDspService.getInstance()?.updateHexString("center", "a")
                    -5 -> HondaDspService.getInstance()?.updateHexString("center", "b")
                    -4 -> HondaDspService.getInstance()?.updateHexString("center", "c")
                    -3 -> HondaDspService.getInstance()?.updateHexString("center", "d")
                    -2 -> HondaDspService.getInstance()?.updateHexString("center", "e")
                    -1 -> HondaDspService.getInstance()?.updateHexString("center", "f")
                    -0 -> HondaDspService.getInstance()?.updateHexString("center", "0")
                    1 -> HondaDspService.getInstance()?.updateHexString("center", "1")
                    2 -> HondaDspService.getInstance()?.updateHexString("center", "2")
                    3 -> HondaDspService.getInstance()?.updateHexString("center", "3")
                    4 -> HondaDspService.getInstance()?.updateHexString("center", "4")
                    5 -> HondaDspService.getInstance()?.updateHexString("center", "5")
                    6 -> HondaDspService.getInstance()?.updateHexString("center", "6")

                }
                HondaDspService.getInstance()?.policzService()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekSub.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                progressSub.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Sub", progress)
                editor.apply()
                when (progress) {
                    -6 -> HondaDspService.getInstance()?.updateHexString("sub", "a")
                    -5 -> HondaDspService.getInstance()?.updateHexString("sub", "b")
                    -4 -> HondaDspService.getInstance()?.updateHexString("sub", "c")
                    -3 -> HondaDspService.getInstance()?.updateHexString("sub", "d")
                    -2 -> HondaDspService.getInstance()?.updateHexString("sub", "e")
                    -1 -> HondaDspService.getInstance()?.updateHexString("sub", "f")
                    -0 -> HondaDspService.getInstance()?.updateHexString("sub", "0")
                    1 -> HondaDspService.getInstance()?.updateHexString("sub", "1")
                    2 -> HondaDspService.getInstance()?.updateHexString("sub", "2")
                    3 -> HondaDspService.getInstance()?.updateHexString("sub", "3")
                    4 -> HondaDspService.getInstance()?.updateHexString("sub", "4")
                    5 -> HondaDspService.getInstance()?.updateHexString("sub", "5")
                    6 -> HondaDspService.getInstance()?.updateHexString("sub", "6")

                }
                HondaDspService.getInstance()?.policzService()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekBalans.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                Balans.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Balans", progress)
                editor.apply()
                when (progress) {
                    -9 -> HondaDspService.getInstance()?.updateHex("bal", 0)
                    -8 -> HondaDspService.getInstance()?.updateHex("bal", 14)
                    -7 -> HondaDspService.getInstance()?.updateHex("bal", 28)
                    -6 -> HondaDspService.getInstance()?.updateHex("bal", 42)
                    -5 -> HondaDspService.getInstance()?.updateHex("bal", 56)
                    -4 -> HondaDspService.getInstance()?.updateHex("bal", 70)
                    -3 -> HondaDspService.getInstance()?.updateHex("bal", 85)
                    -2 -> HondaDspService.getInstance()?.updateHex("bal", 99)
                    -1 -> HondaDspService.getInstance()?.updateHex("bal", 113)
                    -0 -> HondaDspService.getInstance()?.updateHex("bal", 128)
                    1 -> HondaDspService.getInstance()?.updateHex("bal", 141)
                    2 -> HondaDspService.getInstance()?.updateHex("bal", 155)
                    3 -> HondaDspService.getInstance()?.updateHex("bal", 170)
                    4 -> HondaDspService.getInstance()?.updateHex("bal", 184)
                    5 -> HondaDspService.getInstance()?.updateHex("bal", 198)
                    6 -> HondaDspService.getInstance()?.updateHex("bal", 212)
                    7 -> HondaDspService.getInstance()?.updateHex("bal", 226)
                    8 -> HondaDspService.getInstance()?.updateHex("bal", 240)
                    9 -> HondaDspService.getInstance()?.updateHex("bal", 255)
                }
                HondaDspService.getInstance()?.policzService()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })

        seekFader.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                Fader.text = progress.toString()

                //val editor = sharedPreference.edit()
                editor.putInt("Fader", progress)
                editor.apply()
                when (progress) {
                    -9 -> HondaDspService.getInstance()?.updateHex("fader", 0)
                    -8 -> HondaDspService.getInstance()?.updateHex("fader", 14)
                    -7 -> HondaDspService.getInstance()?.updateHex("fader", 28)
                    -6 -> HondaDspService.getInstance()?.updateHex("fader", 42)
                    -5 -> HondaDspService.getInstance()?.updateHex("fader", 56)
                    -4 -> HondaDspService.getInstance()?.updateHex("fader", 70)
                    -3 -> HondaDspService.getInstance()?.updateHex("fader", 85)
                    -2 -> HondaDspService.getInstance()?.updateHex("fader", 99)
                    -1 -> HondaDspService.getInstance()?.updateHex("fader", 113)
                    -0 -> HondaDspService.getInstance()?.updateHex("fader", 128)
                    1 -> HondaDspService.getInstance()?.updateHex("fader", 141)
                    2 -> HondaDspService.getInstance()?.updateHex("fader", 155)
                    3 -> HondaDspService.getInstance()?.updateHex("fader", 170)
                    4 -> HondaDspService.getInstance()?.updateHex("fader", 184)
                    5 -> HondaDspService.getInstance()?.updateHex("fader", 198)
                    6 -> HondaDspService.getInstance()?.updateHex("fader", 212)
                    7 -> HondaDspService.getInstance()?.updateHex("fader", 226)
                    8 -> HondaDspService.getInstance()?.updateHex("fader", 240)
                    9 -> HondaDspService.getInstance()?.updateHex("fader", 255)
                }
                HondaDspService.getInstance()?.policzService()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        })


    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if(EasyPermissions.somePermissionPermanentlyDenied(this,perms)){
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode== AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE){
            //val yes= "Allow"
           // val no = "Deny"
            Toast.makeText(this,"onActivityResult",Toast.LENGTH_LONG).show()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        //it doesnt mean anything serious here
    }

    override fun onRationaleAccepted(requestCode: Int) {

    }

    override fun onRationaleDenied(requestCode: Int) {

    }
///service
    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, HondaDspService::class.java).also {
            it.action = action.name
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode" + it)
                startForegroundService(it)
            //    return
            //}
            //log("Starting the service in < 26 Mode")
            //startService(it)
        }
    }

    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun askForLocationPermission(){
        if(hasLocationPermissions()) {
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
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { _: Location? ->
                    //do nothing
                }
        }
        else{
            EasyPermissions.requestPermissions(this, "need permission to find your location and calculate speed", LOCATION_PERM, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasLocationPermissions() : Boolean {
        return EasyPermissions.hasPermissions(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun updateVolume(volume: Int, Source: String) {
        this@MainActivity.runOnUiThread {

            if (volAppSource == "app" && Source == "app") {
                val textV1 = findViewById<TextView>(R.id.vol2)
                textV1.text = volume.toString()
            }
            if (volAppSource == "sys" && Source == "sys") {
                val textV1 = findViewById<TextView>(R.id.vol2)
                textV1.text = volume.toString()
            }
        }
    }

    fun updateGps(speed: Int, Source: String) {
        this@MainActivity.runOnUiThread {

            if (gpsAppSource == "app" && Source == "app") {
                val textV1 = findViewById<TextView>(R.id.currentSpeedID)
                textV1.text = speed.toString()
            }
            if (gpsAppSource == "sys" && Source == "sys") {
                val textV1 = findViewById<TextView>(R.id.currentSpeedID)
                textV1.text = speed.toString()
            }
        }
    }

    fun updateCrc(crc: String) {
        this@MainActivity.runOnUiThread {

            if (crc == "ok") {
                imageCrc.setImageResource(R.drawable.green_dot)
            }
            else {
                imageCrc.setImageResource(R.drawable.red_dot)
            }
        }
    }

    fun updateAmpErrors(errors: Int) {
        this@MainActivity.runOnUiThread {

            val textV1 = findViewById<TextView>(R.id.ampErrors)
            textV1.text = errors.toString()
        }
    }

    fun updateAns(ans: String) {
        this@MainActivity.runOnUiThread {

            if (ans == "ok") {
                imageAns.setImageResource(R.drawable.green_dot)
            }
            else {
                imageAns.setImageResource(R.drawable.red_dot)
            }
        }
    }

    fun tvAppend(text: CharSequence) {
        this@MainActivity.runOnUiThread {
            val textRec = findViewById<TextView>(R.id.textRec)
            textRec.text = text

        }
    }

    lateinit var m_usbManager: UsbManager
    var m_device: UsbDevice? = null
    var m_serial: UsbSerialDevice? = null
    var m_connection: UsbDeviceConnection? = null
    val ACTION_USB_PERMISSION = "permission"

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
                            //m_serial!!.read(mCallback)
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

    private fun disconnect() {
        m_serial?.close()
    }
}
