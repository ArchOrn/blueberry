part of blueberry;

class Blueberry {
  static const MethodChannel _channel = MethodChannel('blueberry');

  static final Blueberry _instance = Blueberry._();

  static Blueberry get instance => _instance;

  final StreamController<MethodCall> _methodStreamController = StreamController.broadcast();

  Stream<MethodCall> get _methodStream => _methodStreamController.stream;

  Blueberry._() {
    _channel.setMethodCallHandler((MethodCall call) {
      _methodStreamController.add(call);
      return Future(() => null);
    });
  }

  bool _isScanning = false;

  final StreamController<bool> _isScanningController = StreamController.broadcast()..add(false);

  Stream<bool> get isScanning => _isScanningController.stream..listen((e) => _isScanning = e);

  List<BlueberryDevice> _scanResults = [];

  final StreamController<List<BlueberryDevice>> _scanResultsController = StreamController.broadcast()..add([]);

  Stream<List<BlueberryDevice>> get scanResults => _scanResultsController.stream..listen((e) => _scanResults = e);

  Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  Stream<BlueberryDevice> scan({Duration timeout = const Duration(seconds: 10)}) async* {
    // Check permissions
    final permissions = [Permission.bluetooth, Permission.bluetoothScan, Permission.bluetoothConnect];
    final statuses = await permissions.request();
    if (statuses.values
        .where((status) => status.isDenied || status.isPermanentlyDenied || status.isLimited || status.isRestricted)
        .isNotEmpty) {
      // TODO: permission denied
      return;
    }

    // Check if not already scanning
    if (_isScanning) {
      throw Exception('Another scan is already in progress.');
    }

    _isScanningController.add(true);
    _scanResultsController.add([]);

    try {
      await _channel.invokeMethod('startScan');
    } catch (error) {
      _isScanningController.add(false);
      rethrow;
    }

    yield* _methodStream
        .where((mc) => mc.method == "scanResult")
        .map((mc) => mc.arguments)
        .takeWhile((_) => _isScanning)
        .timeout(timeout, onTimeout: (_) => stopScan())
        .map((args) {
      // Retrieve bluetooth device details from JSON
      final device = BlueberryDevice.fromJson(args);

      // Populate to current scan results and replace if existing
      final results = _scanResults;
      final deviceIndex = results.lastIndexWhere((result) => result.address == device.address);
      if (deviceIndex > -1) {
        results[deviceIndex] = device;
      } else {
        results.add(device);
      }
      _scanResultsController.add(results);

      return device;
    });
  }

  Future<List<BlueberryDevice>> startScan({Duration timeout = const Duration(seconds: 10)}) async {
    await scan(timeout: timeout).drain();
    return _scanResults;
  }

  Future<void> stopScan() async {
    await _channel.invokeMethod('stopScan');
    _isScanningController.add(false);
  }
}
