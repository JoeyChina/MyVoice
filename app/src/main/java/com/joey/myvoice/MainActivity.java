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
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.joey.myvoice.control.MyRecognizer;
import com.joey.myvoice.control.MyWakeup;
import com.joey.myvoice.pinyin.PinyinSimilarity;
import com.joey.myvoice.recognization.ChainRecogListener;
import com.joey.myvoice.recognization.CommonRecogParams;
import com.joey.myvoice.recognization.IStatus;
import com.joey.myvoice.recognization.MessageStatusRecogListener;
import com.joey.myvoice.recognization.offline.OfflineRecogParams;
import com.joey.myvoice.recognization.online.OnlineRecogParams;
import com.joey.myvoice.ui.BaiduASRDigitalDialog;
import com.joey.myvoice.ui.DigitalDialogInput;
import com.joey.myvoice.ui.SimpleTransApplication;

import java.util.ArrayList;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements IStatus {
    private String TAG = "MyActivity";

    private Button btn_start;

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
        initView();
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

    private void initView() {
        btn_start = (Button) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
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

    /**
     * 开始录音，点击“开始”按钮后调用。
     */

    protected void start() {


        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> params = apiParams.fetch(sp);  // params可以手动填入

        // BaiduASRDigitalDialog的输入参数
        input = new DigitalDialogInput(myRecognizer, listener, params);

        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);
        // 在BaiduASRDialog中读取, 因此需要用 SimpleTransApplication传递input参数
        ((SimpleTransApplication) getApplicationContext()).setDigitalDialogInput(input);

        // 修改对话框样式
        // intent.putExtra(BaiduASRDigitalDialog.PARAM_DIALOG_THEME, BaiduASRDigitalDialog.THEME_ORANGE_DEEPBG);

        running = true;
        startActivityForResult(intent, 2);

    }



    /**
     * 开始录音后，手动停止录音。SDK会识别在此过程中的录音。点击“停止”按钮后调用。
     */
    private void stop() {
        myRecognizer.stop();
    }

    /**
     * 开始录音后，取消这次录音。SDK会取消本次识别，回到原始状态。点击“取消”按钮后调用。
     */
    private void cancel() {
        myRecognizer.cancel();
    }




    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        running = false;
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
            Message msg = handler.obtainMessage();
            msg.what = STATUS_FINISHED;
            msg.arg2 = 1;
            msg.obj = message;
            handler.sendMessage(msg);
        }

    }


    /**
     * 销毁时需要释放识别资源。
     */
    @Override
    protected void onDestroy() {
        myRecognizer.release();
        Log.i(TAG, "onDestory");
        super.onDestroy();
        if (!running) {
            myRecognizer.release();
        }

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
