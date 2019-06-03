package com.len.speech;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public class DictSpeechRecognizer implements RecognitionListener {

    private static final String TAG = "DictSpeechRecognizer";

    private SpeechRecognizer mSpeechRecognizer;

    private DictSpeechListener mDictSpeechListener;

    private Intent mRecognitionIntent;

    public void init(Context context, DictSpeechListener dictSpeechListener) {
        this.mDictSpeechListener = dictSpeechListener;
        if (mSpeechRecognizer == null) {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            Log.d(TAG, "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(context));
            mSpeechRecognizer.setRecognitionListener(this);
        }

        if (mRecognitionIntent == null) {
            mRecognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
//            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        }
    }

    void setLanguage(String language) {
        // 中文 zh-CN 英语en_US
        // 语音种类
        Locale mLocale;
        if ("zh-CN".equals(language)) {
            mLocale = Locale.CHINA;
        } else {
            mLocale = Locale.US;
        }
        mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, mLocale);
//        mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        Log.d(TAG, "language: " + mLocale.toString());
    }

    void startListening() {
        mSpeechRecognizer.startListening(mRecognitionIntent);
    }

    void stopListening() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.stopListening();
        }
    }

    public void cancel() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.cancel();
        }
    }

    public void destroy() {
        Log.d(TAG, "destroy: ");
        stopListening();
        cancel();
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.setRecognitionListener(null);
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "onReadyForSpeech: ");
        mDictSpeechListener.startListening();
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech: ");
    }

    @Override
    public void onRmsChanged(float rmsdb) {
        mDictSpeechListener.onRmsChanged(rmsdb);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.d(TAG, "onBufferReceived: ");
    }

    @Override
    public void onEndOfSpeech() {
        mDictSpeechListener.onEndOfSpeech();
    }

    @Override
    public void onError(int error) {
        Log.d(TAG, "onError: " + error);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> list = new ArrayList<>();
        Object obj = results.get(SpeechRecognizer.RESULTS_RECOGNITION);
        if (obj instanceof ArrayList) {
            list = (ArrayList<String>) obj;
        }
        mDictSpeechListener.onResults(list);

    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        Object value = partialResults.get("results_recognition");
        if (value == null) {
            mDictSpeechListener.onPartsResult("");
            return;
        }

        try {
            Log.d(TAG, "onPartialResults: " + value.toString());
            // onPartialResults 结果可能是 json array 形式
            if (value.toString().startsWith("[")) {
                JSONArray array = new JSONArray(value.toString());
                Log.d(TAG, "array: " + array.toString());
                if (array.length() > 0) {
                    Log.d(TAG, "send: " + array.get(0).toString());
                    mDictSpeechListener.onPartsResult(array.get(0).toString());
                }
                return;
            }

            JSONObject parts = new JSONObject(value.toString());
            if (parts.has("recognizeResult")) {
                String result = parts.getString("recognizeResult");
                mDictSpeechListener.onPartsResult(result);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            mDictSpeechListener.onPartsResult("");
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        Log.d(TAG, "onEvent: eventType ---- " + eventType);
        Log.d(TAG, "onEvent: Bundle ---- " + params.toString());
    }

    public interface DictSpeechListener {

        void startListening();

        void onRmsChanged(float rmsdb);

        void onEndOfSpeech();

        void onPartsResult(String part);

        void onResults(ArrayList<String> results);

    }
}
