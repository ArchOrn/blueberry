part of blueberry;

@JsonSerializable()
class BlueberryDevice {
  final String name;
  final String address;

  bool _isConnected = false;

  Stream<bool> get isConnected async* {
    yield await Blueberry._channel.invokeMethod<bool>('isConnected', {'id': address}) ?? false;

    yield* Blueberry.instance._methodStream
        .where((mc) => mc.method == "isConnected")
        .map((mc) => mc.arguments as Map<String, dynamic>)
        .where((data) => data['id'] == address)
        .map((data) => data['value']);

    // TODO: listen stream and populate _isConnected
  }

  BlueberryDevice({required this.name, required this.address});

  factory BlueberryDevice.fromJson(Map<String, dynamic> json) => _$BlueberryDeviceFromJson(json);

  Map<String, dynamic> toJson() => _$BlueberryDeviceToJson(this);

  Future<bool> connect() async {
    if (_isConnected) return true;
    final result = await Blueberry._channel.invokeMethod<bool>('connect', toJson());
    return result ?? false;
  }

  Future<bool> disconnect() async {
    if (!_isConnected) return true;
    final result = await Blueberry._channel.invokeMethod<bool>('disconnect', {'id': address});
    return result ?? false;
  }

  Future<String?> send(List<int> bytes) async {
    if (!_isConnected) return null;
    return Blueberry._channel.invokeMethod('send', {
      'id': address,
      'bytes': bytes,
    });
  }

  Future<String?> sendMessage(String message) async => send(utf8.encode(message));
}
