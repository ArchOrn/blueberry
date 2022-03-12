package io.ardelias.blueberry

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.ardelias.blueberry.util.toArguments
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.util.*


/** BlueberryPlugin */
class BlueberryPlugin : FlutterPlugin, MethodCallHandler {
  private val socketUuid: UUID = UUID.randomUUID();

  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel: MethodChannel

  private var bluetoothManager: BluetoothManager? = null
  private var bluetoothAdapter: BluetoothAdapter? = null

  private var isScanning: Boolean = false

  private val devices: MutableMap<String, BluetoothDevice> = mutableMapOf()
  private val sockets: MutableMap<String, BluetoothSocket> = mutableMapOf()

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private val scanCallback21 = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      val device = result.device
      if (device != null) {
        devices[device.address] = device
        channel.invokeMethod("scanResult", device.toArguments())
      }
    }
  }

  private val scanCallback18 = LeScanCallback { device, _, _ ->
    if (device != null) {
      devices[device.address] = device
      channel.invokeMethod("scanResult", device.toArguments())
    }
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "blueberry")
    channel.setMethodCallHandler(this)
    bluetoothManager = flutterPluginBinding.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    bluetoothAdapter = bluetoothManager?.adapter
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    if (bluetoothAdapter == null) return result.error("bluetooth_unavailable", "Bluetooth is not available", null)

    val address = call.argument<String>("address") ?: call.argument<String>("id")
    when (call.method) {
      "getPlatformVersion" -> result.success("Android ${android.os.Build.VERSION.RELEASE}")
      "startScan" -> startScan(result)
      "stopScan" -> stopScan(result)
      "isConnected" -> {
        if (address == null) return result.error(
            "missing_parameter",
            "You need to pass bluetooth device address as 'address' or 'id' parameter to connect",
            null
        )
        isConnected(result, address)
      }
      "connect" -> {
        if (address == null) return result.error(
            "missing_parameter",
            "You need to pass bluetooth device address as 'address' or 'id' parameter to connect",
            null
        )
        connect(result, address)
      }
      "disconnect" -> disconnect(result, address)
      "send" -> {
        if (address == null) return result.error(
            "missing_parameter",
            "You need to pass bluetooth device address as 'address' or 'id' parameter to send data",
            null
        )
        val bytes = call.argument<List<Int>>("bytes")
            ?: return result.error(
                "missing_parameter",
                "You need to pass data as 'bytes' to send data",
                null
            )
        send(result, address, byteArrayOf(*(bytes.map { it.toByte() }.toByteArray())))
      }
      else -> result.notImplemented()
    }
  }

  private fun startScan(result: Result) {
    if (isScanning) result.error("bluetooth_already_scanning", "A bluetooth device scan is already ongoing", null)

    // Clear scanned devices map and socket map
    devices.clear();
    sockets.values.forEach { it.close() }
    sockets.clear();

    try {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
        scanLowEnergyDevice21()
      else
        scanLowEnergyDevice18()
      isScanning = true
      result.success(null)
    } catch (exception: Exception) {
      result.error("startScan", exception.message, exception)
    }
  }

  private fun stopScan(result: Result) {
    if (!isScanning) result.error("bluetooth_not_scanning", "There is no ongoing bluetooth device scan", null)

    try {
      if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
        scanLowEnergyDevice21(false)
      else
        scanLowEnergyDevice18(false)
      isScanning = false
      result.success(null)
    } catch (exception: Exception) {
      result.error("stopScan", exception.message, exception)
    }
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  private fun scanLowEnergyDevice21(enable: Boolean = true) {
    val scanner = bluetoothAdapter?.bluetoothLeScanner
    if (enable) {
      val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
      scanner?.startScan(null, settings, scanCallback21)
    } else {
      scanner?.stopScan(scanCallback21)
    }
  }

  private fun scanLowEnergyDevice18(enable: Boolean = true) {
    if (enable) {
      bluetoothAdapter?.startLeScan(scanCallback18)
    } else {
      bluetoothAdapter?.stopLeScan(scanCallback18)
    }
  }

  private fun isConnected(result: Result, address: String) {
    // TODO
  }

  private fun connect(result: Result, address: String) {
    // Check bonded devices first then scanned devices
    val device = bluetoothAdapter?.bondedDevices?.firstOrNull { device -> device.address == address }
        ?: devices[address]
        ?: return result.error("bluetooth_device_not_found", "Cannot find the specified bluetooth device", null)

    try {
      // Create socket, connect to it and store it
      val socket = device.createRfcommSocketToServiceRecord(socketUuid)
      socket.connect()
      sockets[address] = socket
      result.success(true)
    } catch (exception: Exception) {
      result.success(false)
    }
  }

  private fun disconnect(result: Result, address: String? = null) {
    try {
      if (address == null) {
        sockets.values.forEach { it.close() }
        sockets.clear();
        return result.success(true)
      }

      val socket = sockets[address] ?: return result.success(false)
      socket.close()
      result.success(true)
    } catch (exception: Exception) {
      result.success(false)
    }
  }

  private fun send(result: Result, address: String, bytes: ByteArray) {
    val socket = sockets[address]
        ?: return result.error(
            "bluetooth_socket_not_found",
            "Cannot find the specified bluetooth socket, are you connected to it?",
            null
        )

    try {
      socket.outputStream.write(bytes)
      val response = socket.inputStream.readBytes()
      // TODO: read proper number of bytes
      result.success(response)
    } catch (e: Exception) {
      return result.error(
          "bluetooth_send_error",
          "Cannot write on the specified bluetooth socket",
          null
      )
    }
  }
}
