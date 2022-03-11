// GENERATED CODE - DO NOT MODIFY BY HAND

part of blueberry;

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BlueberryDevice _$BlueberryDeviceFromJson(Map<String, dynamic> json) =>
    BlueberryDevice(
      name: json['name'] as String,
      address: json['address'] as String,
    );

Map<String, dynamic> _$BlueberryDeviceToJson(BlueberryDevice instance) =>
    <String, dynamic>{
      'name': instance.name,
      'address': instance.address,
    };
