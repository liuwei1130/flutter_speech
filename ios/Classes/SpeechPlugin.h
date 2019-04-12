#import <Flutter/Flutter.h>

@interface SpeechPlugin : NSObject<FlutterPlugin>
-(instancetype) initWithFlutterMethodChannel:(FlutterMethodChannel*) channel;
@end
