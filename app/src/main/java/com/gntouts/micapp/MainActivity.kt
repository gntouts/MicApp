package com.gntouts.micapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var spinnerInput: Spinner
    private lateinit var spinnerOutput: Spinner
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView

    private val passthrough = AudioPassthrough()

    private var inputDevices: List<AudioDeviceInfo> = emptyList()
    private var outputDevices: List<AudioDeviceInfo> = emptyList()

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
            Toast.makeText(
                this,
                "Microphone and Bluetooth permissions are required.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
            refreshDeviceLists()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
            refreshDeviceLists()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        spinnerInput = findViewById(R.id.spinnerInput)
        spinnerOutput = findViewById(R.id.spinnerOutput)
        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)

        btnToggle.setOnClickListener {
            if (passthrough.isRunning) stopPassthrough() else startPassthrough()
        }

        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        refreshDeviceLists()
    }

    override fun onPause() {
        super.onPause()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        passthrough.stop()
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun onPermissionsGranted() {
        refreshDeviceLists()
        btnToggle.isEnabled = true
    }

    private fun refreshDeviceLists() {
        inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

        val inputNames = inputDevices.map { deviceLabel(it) }
        val outputNames = outputDevices.map { deviceLabel(it) }

        spinnerInput.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, inputNames
        )
        spinnerOutput.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, outputNames
        )
    }

    private fun startPassthrough() {
        val inputDevice = inputDevices.getOrNull(spinnerInput.selectedItemPosition)
        val outputDevice = outputDevices.getOrNull(spinnerOutput.selectedItemPosition)
        passthrough.start(inputDevice, outputDevice)
        btnToggle.text = getString(R.string.btn_stop)
        tvStatus.text = getString(R.string.status_active)
        tvStatus.setTextColor(getColor(R.color.status_active))
    }

    private fun stopPassthrough() {
        passthrough.stop()
        btnToggle.text = getString(R.string.btn_start)
        tvStatus.text = getString(R.string.status_stopped)
        tvStatus.setTextColor(getColor(R.color.status_stopped))
    }

    private fun deviceLabel(device: AudioDeviceInfo): String {
        val name = device.productName.toString().trim()
        val typeName = deviceTypeName(device.type)
        return if (name.isNotEmpty() && name != "null") "$name ($typeName)" else typeName
    }

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line Digital"
        else -> "Device (type $type)"
    }
}
