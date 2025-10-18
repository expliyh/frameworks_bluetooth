package com.openvela.bluetoothtest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.openvela.bluetoothtest.bredr.BondActivity
import com.openvela.bluetoothtest.bredr.BredrInquiryActivity
import com.openvela.bluetoothtest.bredr.BredrL2capActivity
import com.openvela.bluetoothtest.LocalAdapter.OnOffActivity
import com.openvela.bluetoothtest.ble.BleL2capActivity
import com.openvela.bluetoothtest.ble.BlePeripheralActivity
import com.openvela.bluetoothtest.ble.BleScanActivity
import com.openvela.bluetoothtest.bredr.spp.SppActivity
import com.openvela.bluetoothtest.bredr.spp.ui.theme.BluetoothTestSuiteTheme
import com.openvela.bluetoothtest.ui.MainDestination
import com.openvela.bluetoothtest.ui.MainRoute
import com.openvela.bluetoothtest.ui.MainWearRoute

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runOnWatch = isRunningOnWatch()
        setContent {
            if (runOnWatch) {
                MainWearRoute(
                    destinations = DESTINATIONS,
                    onOpenDestination = ::handleDestinationSelection
                )
            } else {
                BluetoothTestSuiteTheme {
                    MainRoute(
                        destinations = DESTINATIONS,
                        onOpenDestination = ::handleDestinationSelection
                    )
                }
            }
        }
        requestBluetoothPermission()
    }

    private fun handleDestinationSelection(destination: MainDestination) {
        val intent = when (destination) {
            MainDestination.AdapterOnOff -> Intent(this, OnOffActivity::class.java)
            MainDestination.BredrInquiry -> Intent(this, BredrInquiryActivity::class.java)
            MainDestination.BondManagement -> Intent(this, BondActivity::class.java)
            MainDestination.SppSessions -> Intent(this, SppActivity::class.java)
            MainDestination.ClassicL2cap -> Intent(this, BredrL2capActivity::class.java)
            MainDestination.BlePeripheral -> Intent(this, BlePeripheralActivity::class.java)
            MainDestination.BleCentral -> Intent(this, BleScanActivity::class.java)
            MainDestination.BleL2cap -> Intent(this, BleL2capActivity::class.java)
        }
        startActivity(intent)
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val necessaryBluetoothPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (necessaryBluetoothPermissions.isNotEmpty()) {
                Log.d(TAG, "Request Bluetooth permissions")
                ActivityCompat.requestPermissions(this, necessaryBluetoothPermissions, 1)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private val DESTINATIONS: List<MainDestination> = enumValues<MainDestination>().toList()
    }

    private fun isRunningOnWatch(): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) ||
            uiMode == Configuration.UI_MODE_TYPE_WATCH
    }
}
