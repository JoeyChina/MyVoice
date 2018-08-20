package com.joey.myvoice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;

import com.joey.myvoice.control.MyRecognizer;
import com.joey.myvoice.control.MyWakeup;
import com.joey.myvoice.pinyin.PinyinSimilarity;
import com.joey.myvoice.recognization.ChainRecogListener;
import com.joey.myvoice.recognization.CommonRecogParams;
import com.joey.myvoice.recognization.IStatus;
import com.joey.myvoice.recognization.MessageStatusRecogListener;
import com.joey.myvoice.recognization.offline.OfflineRecogParams;
import com.joey.myvoice.recognization.online.OnlineRecogParams;
import com.joey.myvoice.ui.DigitalDialogInput;

import java.util.ArrayList;

import static com.joey.myvoice.recognization.IStatus.STATUS_FINISHED;
import static com.joey.myvoice.recognization.IStatus.STATUS_NONE;
import static com.joey.myvoice.recognization.IStatus.STATUS_READY;

public class MainActivity extends AppCompatActivity implements IStatus {
    private String TAG = "MyActivity";

    /**
     * 识别控制器，使用MyRecognizer控制识别的流程
     */
    protected MyRecognizer myRecognizer;

    /*
     * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
     */
    protected CommonRecogParams apiParams;

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

    boolean running = false;

    /**
     * 控制UI按钮的状态
     */
    protected int status;

    protected Handler handler;
    protected MyWakeup myWakeup;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initPermission();
        initRecog();

        handler = new Handler() {

            /*
             * @param msg
             */
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                handleMsg(msg);
            }

        };

    }

    /**
     * 在onCreate中调用。初始化识别控制类MyRecognizer
     */
    protected void initRecog() {
        listener = new ChainRecogListener();
        // DigitalDialogInput 输入 ，MessageStatusRecogListener可替换为用户自己业务逻辑的listener
        listener.addListener(new MessageStatusRecogListener(handler));
        myRecognizer = new MyRecognizer(this, listener); // DigitalDialogInput 输入
        apiParams = getApiParams();
        status = STATUS_NONE;
        if (enableOffline) {
            myRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        }
    }

    private CommonRecogParams getApiParams() {
        return new OnlineRecogParams(this);
    }







    void handleMsg(Message msg) {
        switch (msg.what) { // 处理MessageStatusRecogListener中的状态回调
            case STATUS_FINISHED:
                if (msg.arg2 == 1) {
///                   et_result.setText(msg.obj.toString());

                    setlogNoSpace("语音指令: " + msg.obj.toString(), changeToOurWords(msg.obj.toString()) + "\n");


                }
                status = STATUS_NONE;

                break;
            case STATUS_NONE:
            case STATUS_READY:
            case STATUS_SPEAKING:
            case STATUS_RECOGNITION:
                status = msg.what;

                break;
            case STATUS_WAKEUP_SUCCESS:
                Log.i(TAG, "HHHH 语音唤醒成功");

                break;
            default:
                break;

        }
    }





    private void setlogNoSpace(String reques, String result) {


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
     * android 6.0 以上需要动态申请权限
     */
    private void initPermission() {
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
