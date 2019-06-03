package com.len.speech_example;

import android.os.Bundle;

import com.len.speech.DictSpeechRecognizer;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;

public class MainActivity extends FlutterActivity {

  DictSpeechRecognizer dictSpeechRecognizer;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    dictSpeechRecognizer = new DictSpeechRecognizer(MainActivity.this);



  }
}
