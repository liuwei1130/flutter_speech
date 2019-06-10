package com.len.speech;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.util.ArrayList;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * SpeechPlugin
 */
public class SpeechPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener,
        PluginRegistry.RequestPermissionsResultListener, DictSpeechRecognizer.DictSpeechListener {

    private static final String TAG = "SpeechPlugin";

    private static final int PERMISSION_REQ_CODE = 1001;

    private DictSpeechRecognizer mSpeechRecognizer;

    private MethodChannel mChannel;

    private Registrar mRegistrar;

    private Result mResult;

    private String mSendResult;

//    private PermissionPageUtils mPageUtils;

    public void setChannel(MethodChannel channel) {
        this.mChannel = channel;
    }

    private void setRegistrar(Registrar registrar) {
        this.mRegistrar = registrar;
        mRegistrar.addActivityResultListener(this);
        mRegistrar.addRequestPermissionsResultListener(this);
    }

    private void initSpeechRecognizer() {
        mSpeechRecognizer = new DictSpeechRecognizer();
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "speech");
        SpeechPlugin speechPlugin = new SpeechPlugin();
        speechPlugin.initSpeechRecognizer();
        speechPlugin.setChannel(channel);
        speechPlugin.setRegistrar(registrar);
        channel.setMethodCallHandler(speechPlugin);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        mResult = result;
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "speech.activate":
                requestPermissions(mRegistrar.activity(), result);
                break;
            case "speech.start":
                if (mSpeechRecognizer.init(mRegistrar.activity().getApplicationContext(), this)) {
                    String language = call.arguments.toString();
                    mSpeechRecognizer.setLanguage(language);
                    mSpeechRecognizer.startListening();
                } else {

                }
                break;
            case "speech.cancel":
                mSpeechRecognizer.cancel();
                mSpeechRecognizer.destroy();
                break;
            case "speech.stop":
                mSpeechRecognizer.stopListening();
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public void startListening() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "startListening: ");
        }
        // 语音识别开始
        mChannel.invokeMethod("speech.onRecognitionStarted", null);
    }

    @Override
    public void onRmsChanged(float rmsdb) {
        if (rmsdb < 0) {
            rmsdb = 0;
        }

        if (rmsdb > 10) {
            rmsdb = 10;
        }
        // 语音识别分贝值变化
        mChannel.invokeMethod("speech.db", rmsdb * 0.1);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onRmsChanged: " + rmsdb * 0.1);
        }
    }

    @Override
    public void onEndOfSpeech() {
        // 语音识别结束
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onEndOfSpeech: ");
        }
        mSpeechRecognizer.stopListening();
    }

    @Override
    public void onPartsResult(String part) {
        if (!TextUtils.isEmpty(part)) {
            mSendResult = part;
        }
    }

    @Override
    public void onResults(ArrayList<String> listenResult) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onResults: " + listenResult);
        }
        // 语音识别结果
        String result = "";
        if (listenResult.size() > 0) {
            result = listenResult.get(0);
        }
        if (!TextUtils.isEmpty(result)) {
            mChannel.invokeMethod("speech.onSpeech", result);
            return;
        }

        if (!TextUtils.isEmpty(mSendResult)) {
            mChannel.invokeMethod("speech.onSpeech", mSendResult);
            mSendResult = "";
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permission, int[] grantResults) {
        if (requestCode == PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //用户同意，执行操作
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onRequestPermissionsResult: grant");
                }
                mResult.success(true);
            } else {
                //用户不同意，向用户展示该权限作用
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onRequestPermissionsResult: define");
                }
                mResult.success(false);
            }
        }
        return false;
    }

    /**
     * 请求麦克风权限
     *
     * @param activity
     */
    private void requestPermissions(Activity activity, Result result) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onMethodCall: permission has granted");
            }
            result.success(true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onMethodCall: start request permission");
            }
            String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            ActivityCompat.requestPermissions(activity, permissions, PERMISSION_REQ_CODE);
        } else {
            result.success(true);
        }
    }

    static void debugLog(String tag, String msg) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg);
        }
    }

}
