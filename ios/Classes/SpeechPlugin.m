#import "SpeechPlugin.h"
#import <AVFoundation/AVFoundation.h>  // 录音
#import <Speech/Speech.h>    // 语音识别

@interface SpeechPlugin ()<SFSpeechRecognizerDelegate> {
  FlutterMethodChannel *_channel;
  SFSpeechRecognizer *_speechRecognizerEn;
  SFSpeechRecognizer *_speechRecognizerCn;
  AVAudioEngine *_audioEngine;
  SFSpeechAudioBufferRecognitionRequest *_speechAudioBufferRecognitionRequest;
  SFSpeechRecognitionTask *_speechRecognitionTask;
  AVAudioRecorder *_recorder;
  NSTimer *_levelTimer;
}
@end

@implementation SpeechPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  FlutterMethodChannel *channel = [FlutterMethodChannel
      methodChannelWithName:@"speech"
            binaryMessenger:[registrar messenger]];
  SpeechPlugin *instance = [[SpeechPlugin alloc] initWithFlutterMethodChannel:channel];
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (instancetype)initWithFlutterMethodChannel:(FlutterMethodChannel *)channel {
  if (self = [super init]) {
    _channel = channel;
    _speechRecognizerCn =
        [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:@"zh-CN"]];
    _speechRecognizerEn =
        [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:@"en_US"]];
    _audioEngine = [[AVAudioEngine alloc] init];
  }
  return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
  if ([@"getPlatformVersion" isEqualToString:call.method]) {
    result([@"iOS " stringByAppendingString:[[UIDevice currentDevice] systemVersion]]);
  } else if ([@"toOpenPermission" isEqualToString:call.method]) {
      [[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString]];
  }  else if ([@"speech.activate" isEqualToString:call.method]) {
    [self activateRecognition:result];
  } else if ([@"speech.start" isEqualToString:call.method]) {
    [self startRecognition:call.arguments :result];
  } else if ([@"speech.cancel" isEqualToString:call.method]) {
    [self cancelRecognition:result];
  } else if ([@"speech.stop" isEqualToString:call.method]) {
    [self stopRecognition:result];
  } else {
    result(FlutterMethodNotImplemented);
  }
}

// 请求权限
- (void)activateRecognition:(FlutterResult)result {
    SFSpeechRecognizerAuthorizationStatus status = [SFSpeechRecognizer authorizationStatus];
    if (status == SFSpeechRecognizerAuthorizationStatusRestricted || status == SFSpeechRecognizerAuthorizationStatusDenied) {
        result([NSNumber numberWithBool:NO]);
    } else if (status == SFSpeechRecognizerAuthorizationStatusAuthorized) {
        [self activateAuthorization:result];
    } else {
        [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus status) {
            if (status == SFSpeechRecognizerAuthorizationStatusAuthorized) {
                 [self activateAuthorization:result];
            } else{
            result(nil);
            }
        }];
    }
}

// 申请麦克风权限 
- (void)activateAuthorization:(FlutterResult)result {
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
    if (status == AVAuthorizationStatusRestricted || status == AVAuthorizationStatusDenied) {
        result([NSNumber numberWithBool:NO]);
    } else if (status == AVAuthorizationStatusAuthorized) {
        result([NSNumber numberWithBool:YES]);
    } else {
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeAudio completionHandler:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                result(granted ? [NSNumber numberWithBool:YES] : nil);
            });
        }];
    }
}

- (void)startRecognition:(NSString *)local :(FlutterResult)result {
  NSLog(@"startRecognition...");
  if (_audioEngine.isRunning) {
    [_audioEngine stop];
    if (_speechAudioBufferRecognitionRequest) {
      [_speechAudioBufferRecognitionRequest endAudio];
    }
    result(@(NO));
  } else {
    bool resultTemp = YES;
    @try {
      [self start:local];
    } @catch (NSException *exception) {
      resultTemp = NO;
      NSLog(@"%@", exception);
    } @finally {
      result(@(resultTemp));
    }
  }
}

- (void)cancelRecognition:(FlutterResult)result {
  NSLog(@"cancelRecognition...");
  if (_speechRecognitionTask) {
    [_speechRecognitionTask cancel];
    _speechRecognitionTask = nil;
    if (result) {
      result(@(NO));
    }
  }

    [self recorderSoundEnd];
    AVAudioSession *session = [AVAudioSession sharedInstance];
    NSError * error = nil;
    [session setCategory:AVAudioSessionCategoryAmbient withOptions:AVAudioSessionCategoryOptionDuckOthers error:&error];
    if (error) {
        NSLog(@"%@",error);
    }
    [session setActive:YES withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];

    [session setActive:NO withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
}

- (void)stopRecognition:(FlutterResult)result {
  NSLog(@"stopRecognition...");
  if (_audioEngine.isRunning) {
    [_audioEngine stop];
    if (_speechAudioBufferRecognitionRequest) {
      [_speechAudioBufferRecognitionRequest endAudio];
    }
  }
  [self recorderSoundEnd];
  result(@(NO));
}

- (void)start:(NSString *)local {

  [self cancelRecognition:nil];

  AVAudioSession *audioSession = [AVAudioSession sharedInstance];
  @try {
    [audioSession setCategory:AVAudioSessionCategoryPlayAndRecord
                         mode:AVAudioSessionModeMeasurement
                      options:AVAudioSessionCategoryOptionDuckOthers error:nil];
    [audioSession setActive:YES
                withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation error:nil];
  } @catch (NSException *exception) {
    NSLog(@"%@", exception);
  }

  _speechAudioBufferRecognitionRequest = [[SFSpeechAudioBufferRecognitionRequest alloc] init];

  if (!_speechAudioBufferRecognitionRequest) {
    NSLog(@"Unable to created a SFSpeechAudioBufferRecognitionRequest object");
  }

  _speechAudioBufferRecognitionRequest.shouldReportPartialResults = YES;

  AVAudioInputNode *inputNode = _audioEngine.inputNode;
  SFSpeechRecognizer *speechRecognizer = [self getRecognizer:local];
  _speechRecognitionTask =
      [speechRecognizer
          recognitionTaskWithRequest:_speechAudioBufferRecognitionRequest
                       resultHandler:^(SFSpeechRecognitionResult *_Nullable result,
                                       NSError *_Nullable error) {
                         NSLog(@"is final: %d  result: %@",
                               result.isFinal,
                               result.bestTranscription.formattedString);
                         bool isFinal = false;
                         if (result) {
                           [_channel invokeMethod:@"speech.onSpeech"
                                        arguments:result.bestTranscription.formattedString];
                           isFinal = result.isFinal;
                           if (isFinal) {
                             [_channel invokeMethod:@"speech.onRecognitionComplete"
                                          arguments:result.bestTranscription
                                 .formattedString];

                           }
                         }
                         if (error) {
                             [_channel invokeMethod:@"speech.onError" arguments:nil];
                         }

                       }];

  AVAudioFormat *recordingFormat = [inputNode outputFormatForBus:0];

  [inputNode removeTapOnBus:0];
  [inputNode installTapOnBus:0 bufferSize:1024 format:recordingFormat block:^(AVAudioPCMBuffer *_Nonnull
  buffer, AVAudioTime *_Nonnull when) {
    [_speechAudioBufferRecognitionRequest appendAudioPCMBuffer:buffer];
  }];

  [_audioEngine prepare];
  @try {
    [_audioEngine startAndReturnError:nil];

    [self recorderSoundStart];

  } @catch (NSException *exception) {
    NSLog(@"%@", exception);
  }

  [_channel invokeMethod:@"speech.onRecognitionStarted" arguments:nil];
}

- (SFSpeechRecognizer *)getRecognizer:(NSString *)local {
  if ([@"en_US" isEqualToString:local]) {
    return _speechRecognizerEn;
  } else {
    return _speechRecognizerCn;
  }
}

#pragma mark - 录音相关
- (void)createAudioRecorder {
  // 实例化录音器对象
  NSError *errorRecord = nil;
  // 不需要保存录音
  _recorder =
      [[AVAudioRecorder alloc] initWithURL:[NSURL fileURLWithPath:@"/dev/null"]
                                  settings:[self getAudioSetting] error:&errorRecord];
  _recorder.meteringEnabled = YES; //如果要监控声波则必须设置为YES

    // 准备录音
    [_recorder prepareToRecord];
}

- (void)recorderSoundStart {
  // 停止之前的录音
  if ([_recorder isRecording]) {
    [_recorder stop];
  }

    // 实例化录音对象
    [self createAudioRecorder];

  if (![_recorder isRecording]) {

    [_recorder record];

    _levelTimer = [NSTimer scheduledTimerWithTimeInterval:0.5 target:self selector:@selector
    (levelTimerCallback:)                        userInfo:nil repeats:YES];

  }
}

- (void)recorderSoundEnd {
  // 停止录音
  if ([_recorder isRecording]) {
    [_recorder stop];
  }
  if (_levelTimer) {
    [_levelTimer invalidate];
    _levelTimer = nil;
  }
}

/* 该方法确实会随环境音量变化而变化，但具体分贝值是否准确暂时没有研究 */
- (void)levelTimerCallback:(NSTimer *)timer {
  [_recorder updateMeters];

  double level;                // The linear 0.0 .. 1.0 value we need.
  float minDecibels = -80.0f; // Or use -60dB, which I measured in a silent room.
  float decibels = [_recorder averagePowerForChannel:0];

  if (decibels < minDecibels) {
    level = 0.0f;
  } else if (decibels >= 0.0f) {
    level = 1.0f;
  } else {
    float root = 2.0f;
    float minAmp = powf(10.0f, 0.05f * minDecibels);
    float inverseAmpRange = 1.0f / (1.0f - minAmp);
    float amp = powf(10.0f, 0.05f * decibels);
    float adjAmp = (amp - minAmp) * inverseAmpRange;

    level = powf(adjAmp, 1.0f / root);
  }

  /* level 范围[0 ~ 1] */
  [_channel invokeMethod:@"speech.db" arguments:[NSNumber numberWithDouble:level]];
}

- (NSDictionary *)getAudioSetting {
  return [NSDictionary dictionaryWithObjectsAndKeys:
                  [NSNumber numberWithFloat: 44100.0], AVSampleRateKey,
                  [NSNumber numberWithInt: kAudioFormatAppleLossless], AVFormatIDKey,
                  [NSNumber numberWithInt: 2], AVNumberOfChannelsKey,
                  [NSNumber numberWithInt: AVAudioQualityMax], AVEncoderAudioQualityKey,
                  nil];
}

#pragma mark - SFSpeechRecognizerDelegate

- (void)speechRecognizer:(SFSpeechRecognizer *)speechRecognizer availabilityDidChange:(BOOL)available {
  [_channel invokeMethod:@"speech.onSpeechAvailability" arguments:@(available)];
}

@end
