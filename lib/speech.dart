import 'dart:async';

import 'dart:ui';
import 'package:flutter/services.dart';

typedef void ValueCallback<T>(T);

class Speech {
  static const MethodChannel _channel = const MethodChannel('speech');

  static final Speech _speech = new Speech._internal();

  factory Speech() => _speech;

  Speech._internal() {
    _channel.setMethodCallHandler(_platformCallHandler);
  }

  /// 检测是权限是否可用
  ValueCallback<bool> _availabilityCallback;

  /// 语音识别是否开始
  VoidCallback _recognitionStartedCallback;

  /// 返回语音中麦克风的大小，数值在dart 层需要 0.0 ～ 1.0, 各个平台自行处理
  ValueCallback<double> _recognitionDbCallback;

  /// 语音识别返回数据回调
  ValueCallback<String> _recognitionResultCallback;

  /// 语音识别结束回调
  ValueCallback<String> _recognitionCompleteCallback;

  /// 语音识别异常回调
  VoidCallback _errorCallback;

  void setAvailabilityCallback(ValueCallback<bool> callback) => _availabilityCallback = callback;

  void setRecognitionResultCallback(ValueCallback<String> callback) =>
      _recognitionResultCallback = callback;

  void setRecognitionStartedCallback(VoidCallback callback) => _recognitionStartedCallback = callback;

  void setRecognitionDbCallback(ValueCallback<double> callback) =>
      _recognitionDbCallback = callback;

  void setRecognitionCompleteCallback(ValueCallback<String> callback) =>
      _recognitionCompleteCallback = callback;

  void setErrorCallback(VoidCallback callback) => _errorCallback = callback;

  Future _platformCallHandler(MethodCall call) async {
    print('_platformCallHandler call ${call.method} ${call.arguments}');
    switch (call.method) {
      case 'speech.onSpeechAvailability':
        _availabilityCallback(call.arguments);
        break;
      case 'speech.onSpeech':
        if (_recognitionResultCallback != null) {
          _recognitionResultCallback(call.arguments as String);
        }
        break;
      case 'speech.onRecognitionStarted':
        _recognitionStartedCallback();
        break;
      case 'speech.onRecognitionComplete':
        if (_recognitionCompleteCallback != null) {
          _recognitionCompleteCallback(call.arguments);
        }
        break;
      case 'speech.db':
        if (_recognitionDbCallback != null) {
          _recognitionDbCallback(call.arguments);
        }
        break;
      case 'speech.onError':
        _errorCallback();
        break;
      default:
        print('Unknowm method ${call.method} ');
    }
  }

  /// 访问权限
  Future<bool> activate() => _channel.invokeMethod('speech.activate');

  /// 开启权限设置
  Future<void> toOpenAccess() {
    return _channel.invokeMethod('toOpenPermission');
  }

  /// start
  Future<bool> start({String locale}) => _channel.invokeMethod('speech.start', locale);

  /// cancel speech
  Future<bool> cancel() => _channel.invokeMethod('speech.cancel');

  /// stop listening
  Future<bool> stop() => _channel.invokeMethod('speech.stop');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
