package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import io.github.muntashirakon.adb.android.AdbMdns;

/** A normal freeform activity, independent of application overlay visibility. */
public final class WirelessPairingActivity extends Activity {
    static void launch(Activity from){
        Intent intent=new Intent(from,WirelessPairingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.ActivityOptions options=android.app.ActivityOptions.makeBasic();
        try{
            org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(android.app.ActivityOptions.class,options,"setLaunchWindowingMode",5);
            android.graphics.Rect screen=from.getWindowManager().getMaximumWindowMetrics().getBounds();
            float density=from.getResources().getDisplayMetrics().density;
            int margin=Math.round(20*density),width=Math.min(Math.round(320*density),screen.width()-2*margin),height=Math.min(Math.round(440*density),screen.height()-2*margin);
            options.setLaunchBounds(new android.graphics.Rect(screen.right-margin-width,screen.top+margin,screen.right-margin,screen.top+margin+height));
            from.startActivity(intent,options.toBundle());
        }catch(ReflectiveOperationException|RuntimeException e){
            android.util.Log.i("GlassWireless","Freeform request unavailable: "+e.getClass().getSimpleName());
            from.startActivity(intent);
        }
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private AdbMdns discovery;
    private TextView state,layoutHint;
    private EditText code;
    private Button submit;
    private int port=-1;
    private boolean busy,connected;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);MobileHelper.init(this);stopService(new Intent(this,WirelessPairingService.class));
        getWindow().setStatusBarColor(0xff10191c);getWindow().setNavigationBarColor(0xff10191c);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff10191c);setContentView(scroll);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(20),dp(20),dp(20));scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        page.addView(label("连接这台手机",24));
        page.addView(label("在设置中打开无线调试，点「使用配对码配对设备」，再在这里输入 6 位码。",14));
        layoutHint=label("正在打开配对小窗…",13);page.addView(layoutHint);
        state=label("等待系统配对窗口，地址和端口会自动发现。",15);state.setTextColor(0xffa4e6d6);page.addView(state);
        code=new EditText(this);code.setTextColor(Color.WHITE);code.setHintTextColor(0xffa6b9bb);code.setHint("6 位配对码");code.setSingleLine();code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        code.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);page.addView(code,new LinearLayout.LayoutParams(-1,dp(60)));
        submit=button(page,"配对并自动连接",this::pair);submit.setEnabled(false);
        code.setOnEditorActionListener((v,a,event)->{if(a==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){pair();return true;}return false;});
        button(page,"打开无线调试",this::openSettings);
        button(page,"找不到开发者选项？",()->DeveloperOptionsGuide.show(this));
        button(page,"改从开发者选项进入",()->WirelessSettings.openDeveloperOptions(this));
        button(page,"返回玻璃投影",()->{startActivity(new Intent(this,DesktopActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));finishAndRemoveTask();});
        scroll.requestApplyInsets();main.postDelayed(()->{updateLayoutHint();if(saved==null&&isInMultiWindowMode())openSettings();},500);
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextColor(0xffedf4f3);v.setTextSize(size);v.setPadding(0,dp(10),0,dp(10));return v;}
    private Button button(LinearLayout page,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());page.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));return b;}
    private void openSettings(){
        if(!DeveloperOptionsGuide.enabled(this)){DeveloperOptionsGuide.show(this);return;}
        if(!isInMultiWindowMode()){layoutHint.setText("请先从最近任务把「玻璃投影配对」切换为小窗，再打开设置。");return;}
        WirelessSettings.open(this);
        main.postDelayed(this::updateLayoutHint,1500);
    }
    private void updateLayoutHint(){layoutHint.setText(isInMultiWindowMode()?"可拖动小窗避开配对码；无需通知输入。":"若未自动打开小窗，请从最近任务将「玻璃投影配对」切换为小窗。");}
    @Override public void onMultiWindowModeChanged(boolean value,Configuration configuration){super.onMultiWindowModeChanged(value,configuration);updateLayoutHint();}
    @Override public void onStart(){
        super.onStart();discovery=new AdbMdns(this,AdbMdns.SERVICE_TYPE_TLS_PAIRING,(host,p)->main.post(()->{
            if(isDestroyed())return;port=p;if(!busy&&!connected){state.setText(p>0?"已找到本机配对窗口，请输入当前 6 位码。":"等待系统配对窗口，地址和端口会自动发现。");submit.setEnabled(p>0);}
        }));
        try{discovery.start();}catch(RuntimeException e){state.setText("未能搜索本机端口，请开启无线调试后返回。");}
    }
    private void pair(){
        if(busy||connected)return;String input=code.getText().toString().trim();
        if(port<=0||!input.matches("[0-9]{6}")){state.setText("请保持系统配对窗口打开，并输入当前 6 位码。");return;}
        busy=true;submit.setEnabled(false);code.setEnabled(false);code.setText("");getSystemService(InputMethodManager.class).hideSoftInputFromWindow(code.getWindowToken(),0);state.setText("正在配对…");
        WirelessAdb.pair(this,port,input,success->{
            if(isDestroyed())return;if(!success){failed("配对未成功，请重新打开系统配对窗口并输入新码。");return;}
            state.setText("配对成功，正在自动连接并启动助手…");
            WirelessAdb.connect(this,ready->{if(isDestroyed())return;if(ready){connected=true;busy=false;state.setText("助手已连接。返回玻璃投影，继续开启无障碍即可。");}else failed(MobileHelper.message);});
        });
    }
    private void failed(String message){busy=false;state.setText(message);code.setEnabled(true);submit.setEnabled(port>0);}
    @Override public void onStop(){if(discovery!=null){discovery.stop();discovery=null;}super.onStop();}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
