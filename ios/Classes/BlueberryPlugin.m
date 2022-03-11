#import "BlueberryPlugin.h"
#if __has_include(<blueberry/blueberry-Swift.h>)
#import <blueberry/blueberry-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "blueberry-Swift.h"
#endif

@implementation BlueberryPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftBlueberryPlugin registerWithRegistrar:registrar];
}
@end
