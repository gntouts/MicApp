package com.gntouts.micapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var spinnerInput: Spinner
    private lateinit var spinnerOutput: Spinner
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup

    private val passthrough = AudioPassthrough()
    private var isPttMode = false
    private var isRefreshingDevices = false
    private var listenersSetUp = false

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
        modeToggleGroup = findViewById(R.id.modeToggleGroup)

        modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isPttMode = checkedId == R.id.btnModePtt
                if (passthrough.isRunning) stopPassthrough()
                applyModeListeners()
            }
        }

        applyModeListeners()
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
        applyModeListeners()
        if (!listenersSetUp) {
            setupSpinnerListeners()
            listenersSetUp = true
        }
    }

    private fun applyModeListeners() {
        if (isPttMode) {
            // Start pipeline muted so it is warm and ready — no startup latency on press
            if (!passthrough.isRunning) {
                val inputDevice = inputDevices.getOrNull(spinnerInput.selectedItemPosition)
                val outputDevice = outputDevices.getOrNull(spinnerOutput.selectedItemPosition)
                passthrough.muted = true
                passthrough.start(inputDevice, outputDevice)
            }
            btnToggle.text = getString(R.string.btn_ptt)
            btnToggle.setOnClickListener(null)
            btnToggle.setOnTouchListener { _, event ->
                if (!btnToggle.isEnabled) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        passthrough.muted = false
                        tvStatus.text = getString(R.string.status_active)
                        tvStatus.setTextColor(getColor(R.color.status_active))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        passthrough.muted = true
                        tvStatus.text = getString(R.string.status_stopped)
                        tvStatus.setTextColor(getColor(R.color.status_stopped))
                        true
                    }
                    else -> false
                }
            }
        } else {
            // Leaving PTT mode — stop the always-on pipeline
            if (passthrough.isRunning) stopPassthrough()
            passthrough.muted = false
            btnToggle.text = getString(R.string.btn_start)
            btnToggle.setOnTouchListener(null)
            btnToggle.setOnClickListener {
                if (passthrough.isRunning) stopPassthrough() else startPassthrough()
            }
        }
    }

    private fun refreshDeviceLists() {
        isRefreshingDevices = true
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
        // Reset flag after any pending onItemSelected callbacks from adapter replacement have fired
        spinnerInput.post { isRefreshingDevices = false }
    }

    private fun setupSpinnerListeners() {
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isRefreshingDevices) restartStream()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinnerInput.onItemSelectedListener = listener
        spinnerOutput.onItemSelectedListener = listener
    }

    // Stop and restart the active pipeline with current device selections.
    // No-op if nothing is running (continuous mode, stopped).
    private fun restartStream() {
        if (!passthrough.isRunning) return
        val wasActive = !passthrough.muted
        passthrough.stop()
        val inputDevice = inputDevices.getOrNull(spinnerInput.selectedItemPosition)
        val outputDevice = outputDevices.getOrNull(spinnerOutput.selectedItemPosition)
        passthrough.muted = isPttMode   // PTT always restarts muted; continuous restarts unmuted
        passthrough.start(inputDevice, outputDevice)
        if (wasActive && !isPttMode) {
            btnToggle.text = getString(R.string.btn_stop)
            tvStatus.text = getString(R.string.status_active)
            tvStatus.setTextColor(getColor(R.color.status_active))
        }
    }

    private fun startPassthrough() {
        val inputDevice = inputDevices.getOrNull(spinnerInput.selectedItemPosition)
        val outputDevice = outputDevices.getOrNull(spinnerOutput.selectedItemPosition)
        passthrough.muted = false
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
