package io.ardelias.blueberry.util

import android.bluetooth.BluetoothDevice

fun BluetoothDevice.toArguments(): Map<String, Any> = mapOf(
    "name" to name,
    "address" to address
)

