package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

public final class DesktopActivity extends Activity {
    private static final int BG=0xff10191c,CARD=0xff1b272b,TEXT=0xffedf4f3,MUTED=0xffa6b9bb,ACCENT=0xffa4e6d6;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView state,hint;
    private TextView mobileStatus;
    private Button service;
    private SeekBar blur,open,close,holdTime;
    private Switch swipeRestore;
    private final Runnable tick=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,700);}};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);AnimationSettings.init(this);MobileHelper.init(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bar=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bar.left,bar.top,bar.right,bar.bottom);return insets;
        });
        FrameLayout frame=new FrameLayout(this);scroll.addView(frame,new ScrollView.LayoutParams(-1,-2));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(22),dp(24),dp(24));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(680));
        frame.addView(page,new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        TextView mark=text("GLASS / FOLD",11,ACCENT);mark.setLetterSpacing(.18f);page.addView(mark);
        TextView title=text("玻璃投影",32,TEXT);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(9),0,dp(6));page.addView(title);
        page.addView(text("让每一次开合，柔和衔接。",14,MUTED));space(page,24);
        LinearLayout status=card(page);state=text("动画已就绪",17,ACCENT);state.setTypeface(null,Typeface.BOLD);status.addView(state);
        hint=text("在桌面或亮屏锁屏界面，展开或合拢手机即可体验。",13,MUTED);hint.setPadding(0,dp(7),0,dp(14));status.addView(hint);
        service=button(status,"管理桌面服务",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),true);
        section(page,"手机独立运行","启动并授权 Shizuku 后，无需电脑连接。重启手机后需重新启动 Shizuku。");
        LinearLayout mobile=card(page);mobileStatus=text("",13,MUTED);mobile.addView(mobileStatus);
        button(mobile,"连接 / 授权 Shizuku",()->MobileHelper.authorize(this),true);
        button(mobile,"后台运行设置",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))),false);
        mobile.addView(text("退出设置页不会暂停动画，也不在最近任务中保留卡片。请允许后台自启动，并在小米后台设置中取消省电限制。",12,MUTED));
        section(page,"作用范围","选择动画出现的位置，半折悬停时自动恢复正常画面。");
        LinearLayout scope=card(page);
        Switch global=new Switch(this);global.setText("全局启用");global.setTextColor(TEXT);global.setTextSize(16);
        global.setChecked(AnimationSettings.globalEnabled);global.setPadding(0,dp(8),0,dp(8));
        global.setOnCheckedChangeListener((b,checked)->{AnimationSettings.global(checked);refreshStatus();});
        scope.addView(global,new LinearLayout.LayoutParams(-1,dp(56)));
        scope.addView(text("关闭：仅桌面和锁屏。开启：扩展到其他应用的可捕获画面。",13,MUTED));
        section(page,"恢复正常画面","悬停或滑动时，约 220 毫秒平滑回放；继续开合超过 10° 后重新跟随。");
        LinearLayout restore=card(page);
        holdTime=slider(restore,"悬停等待时间","默认 3 秒 · 在此时间内角度摆幅不超过 10° 时恢复。",1,10,1,AnimationSettings.holdSeconds," 秒",v->{AnimationSettings.hold(v);refreshStatus();});
        swipeRestore=new Switch(this);swipeRestore.setText("滑动恢复正常画面");swipeRestore.setTextColor(TEXT);swipeRestore.setTextSize(16);
        swipeRestore.setChecked(AnimationSettings.swipeRestore);swipeRestore.setPadding(0,dp(10),0,dp(8));
        swipeRestore.setOnCheckedChangeListener((b,checked)->AnimationSettings.swipe(checked));
        restore.addView(swipeRestore,new LinearLayout.LayoutParams(-1,dp(60)));
        restore.addView(text("检测到手指滑动就立即回放，静态页面上也有效。轻点不触发，应用仍正常响应手势。",12,MUTED));
        section(page,"玻璃质感","调节雾化程度，保留原有的投影形状。");
        blur=slider(card(page),"模糊强度","100% 为当前默认效果；0% 关闭模糊。",0,200,5,AnimationSettings.blurPercent,"%",AnimationSettings::blur);
        section(page,"切屏时机","展开与合拢分别设置，角度越小越接近合上。");
        LinearLayout angles=card(page);
        open=slider(angles,"展开时切到内屏","默认 60° · 可调 10–170°",10,170,1,AnimationSettings.openAngle,"°",AnimationSettings::open);
        View line=new View(this);line.setBackgroundColor(0xff304044);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(17),0,dp(20));angles.addView(line,lp);
        close=slider(angles,"合拢时切到外屏","默认 120° · 可调 10–170°",10,170,1,AnimationSettings.closeAngle,"°",AnimationSettings::close);
        space(page,12);button(page,"回到桌面体验",()->startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),true);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);page.addView(actions);
        Button reset=button(actions,"恢复默认",()->{AnimationSettings.reset();blur.setProgress(20);open.setProgress(50);close.setProgress(110);holdTime.setProgress(2);swipeRestore.setChecked(false);refreshStatus();Toast.makeText(this,"已恢复：100% · 展开 60° · 合拢 120° · 悬停 3 秒",Toast.LENGTH_SHORT).show();},false);
        reset.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));
        Button pause=button(actions,"暂停动画",()->{ProjectionService.stop();refreshStatus();},false);pause.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));
        TextView foot=text("设置自动保存，下次开合生效。",12,MUTED);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(15),0,0);page.addView(foot);
        scroll.requestApplyInsets();refreshStatus();
    }
    private void refreshStatus(){
        if(state==null)return;
        boolean enabled=ProjectionService.instance!=null;
        boolean ready=enabled&&SystemClock.uptimeMillis()-ProjectionService.helperAt<3000;
        state.setText(ready?"●  动画已就绪":enabled?"○  等待连接":"○  动画已暂停");
        String scope=AnimationSettings.globalEnabled?"已全局启用。":"在桌面或亮屏锁屏界面，展开或合拢手机即可体验。";
        hint.setText(ready?scope+"半折悬停 "+AnimationSettings.holdSeconds+" 秒后恢复正常画面。":enabled?MobileHelper.message:"开启「"+getString(R.string.app_name)+"」无障碍服务后即可使用。");
        if(mobileStatus!=null)mobileStatus.setText(MobileHelper.message);
        service.setText(enabled?"管理桌面服务":"开启桌面服务");
    }
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");return t;}
    private GradientDrawable background(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout card(LinearLayout page){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(20),dp(20),dp(20),dp(20));c.setBackground(background(CARD,24));page.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
    private void section(LinearLayout page,String title,String description){space(page,26);TextView h=text(title,19,TEXT);h.setTypeface(null,Typeface.BOLD);page.addView(h);TextView sub=text(description,13,MUTED);sub.setPadding(0,dp(6),0,dp(14));page.addView(sub);}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(LinearLayout parent,String title,Runnable action,boolean filled){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(filled?BG:ACCENT);
        // Tinting the entire platform background hides its ripple, especially with transparent tint.
        StateListDrawable surface=new StateListDrawable();
        surface.addState(new int[]{android.R.attr.state_pressed},background(filled?0xff79c7b5:0x33a4e6d6,14));
        surface.addState(new int[]{android.R.attr.state_focused},background(filled?0xff8bd4c3:0x22a4e6d6,14));
        surface.addState(new int[]{},background(filled?ACCENT:Color.TRANSPARENT,14));
        b.setBackgroundTintList(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(filled?0x3310191c:0x55a4e6d6),surface,background(Color.WHITE,14)));
        b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));return b;
    }
    private SeekBar slider(LinearLayout card,String title,String description,int min,int max,int step,int initial,String unit,IntConsumer save){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);
        TextView label=text(title,16,TEXT);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=text(initial+unit,25,ACCENT);value.setTypeface(null,Typeface.BOLD);row.addView(value);
        TextView desc=text(description,12,MUTED);desc.setPadding(0,dp(7),0,dp(8));card.addView(desc);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);card.addView(controls);
        SeekBar bar=new SeekBar(this);bar.setMax((max-min)/step);bar.setProgress((initial-min)/step);bar.setContentDescription(title);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));bar.setThumbTintList(ColorStateList.valueOf(ACCENT));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff41575b));
        Button minus=button(controls,"−",()->{bar.setProgress(Math.max(0,bar.getProgress()-1));save.accept(min+bar.getProgress()*step);},false);
        minus.setContentDescription("减小"+title);minus.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        controls.addView(bar,new LinearLayout.LayoutParams(0,dp(48),1));
        Button plus=button(controls,"+",()->{bar.setProgress(Math.min(bar.getMax(),bar.getProgress()+1));save.accept(min+bar.getProgress()*step);},false);
        plus.setContentDescription("增加"+title);plus.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean user){int n=min+progress*step;value.setText(n+unit);b.setStateDescription(n+unit);if(user)save.accept(n);}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });bar.setStateDescription(initial+unit);return bar;
    }
    @Override public void onResume(){super.onResume();handler.removeCallbacks(tick);handler.post(tick);}
    @Override public void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
