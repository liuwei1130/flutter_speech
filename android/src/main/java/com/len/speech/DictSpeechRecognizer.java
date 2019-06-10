package com.len.speech;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.content.pm.PackageManager.MATCH_ALL;

public class DictSpeechRecognizer implements RecognitionListener {

    private static final String TAG = "DictSpeechRecognizer";

    private SpeechRecognizer mSpeechRecognizer;

    private DictSpeechListener mDictSpeechListener;

    private Intent mRecognitionIntent;

    /**
     * @param context            上下文
     * @param dictSpeechListener 语音识别监听
     * @return 是否初始化成功
     */
    public boolean init(Context context, DictSpeechListener dictSpeechListener) {

        // 查找当前系统的使用的语音识别服务
        // com.huawei.vassistant/com.huawei.ziri.service.FakeRecognitionService
        String serviceComponent = Settings.Secure.getString(context.getContentResolver(),
                                                            "voice_recognition_service");

        if (TextUtils.isEmpty(serviceComponent)) {
            return false;
        }

        // 当前系统使用的语音识别服务
        ComponentName component = ComponentName.unflattenFromString(serviceComponent);

        if (component == null) {
            return false;
        }

        boolean isRecognizerServiceValid = false;
        ComponentName currentRecognitionCmp = null;

        // 查找得到的 "可用的" 语音识别服务
        List<ResolveInfo> list = context.getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), MATCH_ALL);
        if (list != null && list.size() != 0) {
            for (ResolveInfo info : list) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "\t" + info.loadLabel(context.getPackageManager()) + ": "
                            + info.serviceInfo.packageName + "/" + info.serviceInfo.name);
                }

                if (info.serviceInfo.packageName.equals(component.getPackageName())) {
                    isRecognizerServiceValid = true;
                    break;
                } else {
                    currentRecognitionCmp = new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
                }

            }
        } else {
            return false;
        }

        this.mDictSpeechListener = dictSpeechListener;

        if (mSpeechRecognizer != null) {
            return true;
        }

        // 当前系统有 语音识别服务
        if (isRecognizerServiceValid) {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        } else {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, currentRecognitionCmp);
        }

        mSpeechRecognizer.setRecognitionListener(this);

        if (mRecognitionIntent == null) {
            mRecognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        }
        return true;
    }

    void setLanguage(String language) {

        if (mRecognitionIntent == null) {
            return;
        }

        // 中文 zh-CN 英语en_US
        // 语音种类
        Locale mLocale;
        if ("zh-CN".equals(language)) {
            mLocale = Locale.CHINA;
        } else {
            mLocale = Locale.US;
        }
        mRecognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, mLocale);
    }

    void startListening() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.startListening(mRecognitionIntent);
        }
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

        if (mSpeechRecognizer != null) {
            stopListening();
            cancel();
            mSpeechRecognizer.setRecognitionListener(null);
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;

        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        mDictSpeechListener.startListening();
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdb) {
        mDictSpeechListener.onRmsChanged(rmsdb);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {
        mDictSpeechListener.onEndOfSpeech();
    }

    @Override
    public void onError(int error) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onError: " + error);
        }
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
            // onPartialResults 结果可能是 json array 形式
            if (value.toString().startsWith("[")) {
                JSONArray array = new JSONArray(value.toString());
                if (array.length() > 0) {
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
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
            mDictSpeechListener.onPartsResult("");
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onEvent: eventType ---- " + eventType);
            Log.d(TAG, "onEvent: Bundle ---- " + params.toString());
        }
    }

    public interface DictSpeechListener {

        void startListening();

        void onRmsChanged(float rmsdb);

        void onEndOfSpeech();

        void onPartsResult(String part);

        void onResults(ArrayList<String> results);

    }
}
