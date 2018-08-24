package com.joey.myvoice;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.baidu.speech.asr.SpeechConstant;
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.joey.myvoice.control.MyRecognizer;
import com.joey.myvoice.control.MyWakeup;
import com.joey.myvoice.pinyin.PinyinSimilarity;
import com.joey.myvoice.recognization.ChainRecogListener;
import com.joey.myvoice.recognization.CommonRecogParams;
import com.joey.myvoice.recognization.IStatus;
import com.joey.myvoice.recognization.MessageStatusRecogListener;
import com.joey.myvoice.recognization.offline.OfflineRecogParams;
import com.joey.myvoice.recognization.online.OnlineRecogParams;
import com.joey.myvoice.tts.control.InitConfig;
import com.joey.myvoice.tts.control.MySyntherizer;
import com.joey.myvoice.tts.control.NonBlockSyntherizer;
import com.joey.myvoice.tts.listener.UiMessageListener;
import com.joey.myvoice.ui.BaiduASRDigitalDialog;
import com.joey.myvoice.ui.DigitalDialogInput;
import com.joey.myvoice.ui.SimpleTransApplication;
import com.joey.myvoice.util.BDAutoCheck;
import com.joey.myvoice.util.OfflineResource;
import com.joey.myvoice.wakeup.IWakeupListener;
import com.joey.myvoice.wakeup.RecogWakeupListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.joey.myvoice.tts.MainHandlerConstant.PRINT;
import static com.joey.myvoice.tts.MainHandlerConstant.UI_CHANGE_INPUT_TEXT_SELECTION;
import static com.joey.myvoice.tts.MainHandlerConstant.UI_CHANGE_SYNTHES_TEXT_SELECTION;

public class MainActivity extends AppCompatActivity implements IStatus {
    private String TAG = "MyActivity";

    private Button btn_start;
    private TextView tv;

    /**
     * 识别控制器，使用MyRecognizer控制识别的流程
     */
    protected MyRecognizer bdRecognizer;

    /*
     * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
     */
    protected CommonRecogParams bdapiParams;

    /*
     * 本Activity中是否需要调用离线命令词功能。根据此参数，判断是否需要调用SDK的ASR_KWS_LOAD_ENGINE事件
     */
    protected boolean enableOffline = false;

    /**
     * 对话框界面的输入参数
     */
    private DigitalDialogInput input;
    /**
     * 有2个listner，一个是用户自己的业务逻辑，如MessageStatusRecogListener。另一个是UI对话框的。
     * 使用这个ChainRecogListener把两个listener和并在一起
     */
    private ChainRecogListener listener;

    boolean bdSpeechRunning = false;

    // TtsMode.MIX; 离在线融合，在线优先； TtsMode.ONLINE 纯在线； 没有纯离线
    protected TtsMode ttsMode = TtsMode.MIX;
    // 主控制类，所有合成控制方法从这个类开始
    protected MySyntherizer synthesizer;
    protected String offlineVoice = OfflineResource.VOICE_FEMALE;


    /**
     * 控制UI按钮的状态
     */
    protected int bdstatus;

    protected Handler bdhandler;
    protected MyWakeup baiduWakeup;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        bdInitPermission();


        bdhandler = new Handler() {

            /*
             * @param msg
             */
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handleMsg(msg);
            }

        };



        /*语音唤醒【语音指令】【语音控制】【听过指令】*/
        IWakeupListener listener = new RecogWakeupListener(bdhandler);
        // 改为 SimpleWakeupListener 后，不依赖handler，但将不会在UI界面上显示
        baiduWakeup = new MyWakeup(this, listener);


        initBaiduTTs();
    }

    private void initView() {
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        tv = (TextView) findViewById(R.id.tv);
    }

    /**
     * 在onCreate中调用。初始化识别控制类MyRecognizer
     */
    protected void bdInitRecog() {
        listener = new ChainRecogListener();
        // DigitalDialogInput 输入 ，MessageStatusRecogListener可替换为用户自己业务逻辑的listener
        listener.addListener(new MessageStatusRecogListener(bdhandler));
        bdRecognizer = new MyRecognizer(this, listener); // DigitalDialogInput 输入
        bdapiParams = getBdapiParams();
        bdstatus = STATUS_NONE;
        if (enableOffline) {
            bdRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        }
    }

    private CommonRecogParams getBdapiParams() {
        return new OnlineRecogParams(this);
    }


    void handleMsg(Message msg) {
        switch (msg.what) { // 处理MessageStatusRecogListener中的状态回调
            case STATUS_FINISHED:
                if (msg.arg2 == 1) {
///                   et_result.setText(msg.obj.toString());
                    setlogNoSpace("语音指令: " + msg.obj.toString(), changeToOurWords(msg.obj.toString()) + "\n");

                    tTSspeak(msg.obj.toString());

                }
                bdstatus = STATUS_NONE;

                break;
            case STATUS_NONE:
            case STATUS_READY:
            case STATUS_SPEAKING:
            case STATUS_RECOGNITION:
                bdstatus = msg.what;

                break;
            case STATUS_WAKEUP_SUCCESS:
                Log.i(TAG, "HHHH 语音唤醒成功"+ bdstatus);
                String wakeString = (String) msg.obj;
                    switch (bdstatus) {
                        case STATUS_NONE: // 初始状态
                            start();
                            Log.i(TAG, "HHHH 弹出"+ bdstatus);
                            bdstatus = STATUS_WAITING_READY;
                            break;
                    }
                break;
            case PRINT:
                Log.i(TAG,"PRINT "+(String) msg.obj);
                break;
            case UI_CHANGE_INPUT_TEXT_SELECTION:

                break;
            case UI_CHANGE_SYNTHES_TEXT_SELECTION:

                break;
            default:
                break;

        }
    }


    private void setlogNoSpace(String reques, String result) {
        tv.setText(tv.getText() + "\n" + reques + "    " + result);

    }

    /**
     * 开始录音，点击“开始”按钮后调用。
     */

    protected void start() {


        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        /**
         * 设置是否有声音
         */
        sp.edit().putBoolean("_tips_sound", true).commit();

        Map<String, Object> params = bdapiParams.fetch(sp);  // params可以手动填入

        // BaiduASRDigitalDialog的输入参数
        input = new DigitalDialogInput(bdRecognizer, listener, params);

        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);
        // 在BaiduASRDialog中读取, 因此需要用 SimpleTransApplication传递input参数
        ((SimpleTransApplication) getApplicationContext()).setDigitalDialogInput(input);

        // 修改对话框样式
        // intent.putExtra(BaiduASRDigitalDialog.PARAM_DIALOG_THEME, BaiduASRDigitalDialog.THEME_ORANGE_DEEPBG);

        bdSpeechRunning = true;
        startActivityForResult(intent, 2);

    }


    /**
     * 开始录音后，手动停止录音。SDK会识别在此过程中的录音。点击“停止”按钮后调用。
     */
    private void stop() {
        bdRecognizer.stop();
    }

    /**
     * 开始录音后，取消这次录音。SDK会取消本次识别，回到原始状态。点击“取消”按钮后调用。
     */
    private void cancel() {
        bdRecognizer.cancel();
    }

    // 点击“开始识别”按钮
    private void startWakeup() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(SpeechConstant.WP_WORDS_FILE, "assets:///WakeUp.bin");
        // "assets:///WakeUp.bin" 表示WakeUp.bin文件定义在assets目录下

        // params.put(SpeechConstant.ACCEPT_AUDIO_DATA,true);
        // params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME,true);
        // params.put(SpeechConstant.IN_FILE,"res:///com/baidu/android/voicedemo/wakeup.pcm");
        // params里 "assets:///WakeUp.bin" 表示WakeUp.bin文件定义在assets目录下
        baiduWakeup.start(params);
    }


    protected void stopWakeup() {
        baiduWakeup.stop();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        bdSpeechRunning = false;
        Log.i(TAG, "requestCode" + requestCode);
        if (requestCode == 2) {
            String message = "";
            if (resultCode == RESULT_OK) {
                ArrayList results = data.getStringArrayListExtra("results");
                if (results != null && results.size() > 0) {
                    message += results.get(0);
                }
            } else {
                message += "没有结果";
            }
            Message msg = bdhandler.obtainMessage();
            msg.what = STATUS_FINISHED;
            msg.arg2 = 1;
            msg.obj = message;
            bdhandler.sendMessage(msg);
        }

    }


    @Override
    protected void onStart() {
        super.onStart();
        startWakeup();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopWakeup();
    }
    /**
     * 销毁时需要释放识别资源。
     */
    @Override
    protected void onDestroy() {
        bdRecognizer.release();
        Log.i(TAG, "onDestory");
        super.onDestroy();
        if (!bdSpeechRunning) {
            bdRecognizer.release();
            baiduWakeup.release();
        }

        synthesizer.release();
    }


    /*
        转换为拼音
     */
    String changeToOurWords(String input) {
        String output = input;

        output = new PinyinSimilarity(false).changeOurWordsWithPinyin(output);

        return output;
    }

    /**
     * 初始化引擎，需要的参数均在InitConfig类里
     * <p>
     * DEMO中提供了3个SpeechSynthesizerListener的实现
     * MessageListener 仅仅用log.i记录日志，在logcat中可以看见
     * UiMessageListener 在MessageListener的基础上，对handler发送消息，实现UI的文字更新
     * FileSaveListener 在UiMessageListener的基础上，使用 onSynthesizeDataArrived回调，获取音频流
     */
    protected void initBaiduTTs() {
        LoggerProxy.printable(true); // 日志打印在logcat中
        // 设置初始化参数
        // 此处可以改为 含有您业务逻辑的SpeechSynthesizerListener的实现类
        SpeechSynthesizerListener listener = new UiMessageListener(bdhandler);

        Map<String, String> params = getParams();
        String appId=null;
        String appKey=null;
        String secretKey=null;

        Bundle metaData = null;
        try {
            metaData = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
            if (params.get(SpeechConstant.APP_ID) != null) {
                appId = params.get(SpeechConstant.APP_ID).toString();
            } else {
                appId = "" + metaData.getInt("com.baidu.speech.APP_ID");
            }
            if (params.get(SpeechConstant.APP_KEY) != null) {
                appKey = params.get(SpeechConstant.APP_KEY).toString();
            } else {
                appKey = metaData.getString("com.baidu.speech.API_KEY");
            }

            if (params.get(SpeechConstant.SECRET) != null) {
                secretKey = params.get(SpeechConstant.SECRET).toString();
            } else {
                secretKey = metaData.getString("com.baidu.speech.SECRET_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // appId appKey secretKey 网站上您申请的应用获取。注意使用离线合成功能的话，需要应用中填写您app的包名。包名在build.gradle中获取。
        InitConfig initConfig = new InitConfig(appId, appKey, secretKey, ttsMode, params, listener);

        // 如果您集成中出错，请将下面一段代码放在和demo中相同的位置，并复制InitConfig 和 AutoCheck到您的项目中
        // 上线时请删除AutoCheck的调用
        BDAutoCheck.getInstance(getApplicationContext()).check(initConfig, new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    BDAutoCheck autoCheck = (BDAutoCheck) msg.obj;
                    synchronized (autoCheck) {
                        String message = autoCheck.obtainDebugMessage();
//                        toPrint(message); // 可以用下面一行替代，在logcat中查看代码
                         Log.w("AutoCheckMessage", message);
                    }
                }
            }

        });
        synthesizer = new NonBlockSyntherizer(this, initConfig, bdhandler); // 此处可以改为MySyntherizer 了解调用过程
    }
    /**
     * 合成的参数，可以初始化时填写，也可以在合成前设置。
     *
     * @return
     */
    protected Map<String, String> getParams() {
        Map<String, String> params = new HashMap<String, String>();
        // 以下参数均为选填
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声 2 特别男声 3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        params.put(SpeechSynthesizer.PARAM_SPEAKER, "0");
        // 设置合成的音量，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_VOLUME, "9");
        // 设置合成的语速，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_SPEED, "6");
        // 设置合成的语调，0-9 ，默认 5
        params.put(SpeechSynthesizer.PARAM_PITCH, "5");

        params.put(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 该参数设置为TtsMode.MIX生效。即纯在线模式不生效。
        // MIX_MODE_DEFAULT 默认 ，wifi状态下使用在线，非wifi离线。在线状态下，请求超时6s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE_WIFI wifi状态下使用在线，非wifi离线。在线状态下， 请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_NETWORK ， 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线
        // MIX_MODE_HIGH_SPEED_SYNTHESIZE, 2G 3G 4G wifi状态下使用在线，其它状态离线。在线状态下，请求超时1.2s自动转离线

        // 离线资源文件， 从assets目录中复制到临时目录，需要在initTTs方法前完成
        OfflineResource offlineResource = createOfflineResource(offlineVoice);
        // 声学模型文件路径 (离线引擎使用), 请确认下面两个文件存在
        params.put(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, offlineResource.getTextFilename());
        params.put(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE,
                offlineResource.getModelFilename());
        return params;
    }


    private void checkResult(int result, String method) {
        if (result != 0) {
            Log.i(TAG,"error code :" + result + " method:" + method + ", 错误码文档:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }

    /**
     * speak 实际上是调用 synthesize后，获取音频流，然后播放。
     * 获取音频流的方式见SaveFileActivity及FileSaveListener
     * 需要合成的文本text的长度不能超过1024个GBK字节。
     */
    private void tTSspeak(String  tts) {
        // 需要合成的文本text的长度不能超过1024个GBK字节。
        if (TextUtils.isEmpty(tts)) {
            tts = "百度语音，面向广大开发者永久免费开放语音合成技术。";
        }
        // 合成前可以修改参数：
        Map<String, String> params = getParams();
        synthesizer.setParams(params);
        int result = synthesizer.speak(tts);
        checkResult(result, "speak");
    }
    /**
     * 暂停播放。仅调用speak后生效
     */
    private void tTSpause() {
        int result = synthesizer.pause();
        checkResult(result, "pause");
    }

    /**
     * 继续播放。仅调用speak后生效，调用pause生效
     */
    private void tTSresume() {
        int result = synthesizer.resume();
        checkResult(result, "resume");
    }

    /*
     * 停止合成引擎。即停止播放，合成，清空内部合成队列。
     */
    private void tTSstop() {
        int result = synthesizer.stop();
        checkResult(result, "stop");
    }

    protected OfflineResource createOfflineResource(String voiceType) {
        OfflineResource offlineResource = null;
        try {
            offlineResource = new OfflineResource(this, voiceType);
        } catch (IOException e) {
            // IO 错误自行处理
            e.printStackTrace();
            Log.w(TAG,"【error】:copy files from assets failed." + e.getMessage());
        }
        return offlineResource;
    }

    /**
     * android 6.0 以上需要动态申请权限
     */
    private void bdInitPermission() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        ArrayList<String> toApplyList = new ArrayList<String>();

        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
                // 进入到这里代表没有权限.

            }
        }
        String[] tmpList = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
        }

    }
}
