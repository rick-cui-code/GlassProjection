package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Observes an independent download; leaving this screen never cancels the transfer. */
public final class UpdateActivity extends Activity {
    private static final int BG=0xff10191c,TEXT=0xffedf4f3,MUTED=0xffa6b9bb,ACCENT=0xffa4e6d6;
    private UpdateCoordinator updates;
    private TextView status,detail,downloadPercent;
    private LinearLayout downloadProgress;
    private ProgressBar downloadBar;
    private Button check,download,pause;
    private boolean starting;
    private int seenFailure;
    private final Runnable observer=this::render;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);updates=UpdateCoordinator.get(this);seenFailure=saved==null?0:saved.getInt("seenFailure");
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bar=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bar.left,bar.top,bar.right,bar.bottom);return insets;});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(page);
        button(page,"返回",this::finish);page.addView(text("检查更新",27,TEXT));page.addView(text("当前版本 "+updates.version(),14,MUTED));
        status=text("",17,ACCENT);status.setPadding(0,dp(24),0,dp(12));page.addView(status);
        check=button(page,"检查更新",()->updates.check(true));
        download=button(page,"下载新版 APK",this::download);
        downloadProgress=new LinearLayout(this);downloadProgress.setOrientation(LinearLayout.VERTICAL);
        downloadProgress.setPadding(0,dp(8),0,dp(4));page.addView(downloadProgress,new LinearLayout.LayoutParams(-1,-2));
        downloadPercent=text("",13,MUTED);downloadPercent.setGravity(Gravity.END);downloadProgress.addView(downloadPercent,new LinearLayout.LayoutParams(-1,-2));
        downloadBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);downloadBar.setMax(100);
        downloadBar.setProgressTintList(ColorStateList.valueOf(ACCENT));downloadBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff34474b));
        LinearLayout.LayoutParams barLayout=new LinearLayout.LayoutParams(-1,dp(8));barLayout.topMargin=dp(6);downloadProgress.addView(downloadBar,barLayout);
        pause=button(page,"暂停下载",()->startService(new Intent(this,UpdateDownloadService.class).setAction("pause")));
        detail=text("",14,MUTED);detail.setPadding(0,dp(20),0,0);page.addView(detail);
        button(page,"打开 GitHub Release",()->openRelease(updates.releasePage()));
        page.addView(text("离开页面仍会继续下载，暂停或断网后可续传。完成后自动打开安装确认；如系统拦截后台弹窗，请点击下载完成通知。安装前仅暂停投影，保留无障碍开关。",12,MUTED));
        scroll.requestApplyInsets();render();if(updates.latest==null)updates.check(false);
    }
    private void render(){
        if(isDestroyed())return;if(updates.downloading||updates.failureId!=seenFailure)starting=false;
        ApkRelease release=updates.offered();boolean offered=updates.isNewer(release);
        boolean busy=starting||updates.checking||updates.downloading||updates.installing;
        check.setEnabled(!busy);download.setEnabled(!busy);download.setAlpha(busy?.55f:1f);
        download.setVisibility(offered?View.VISIBLE:View.GONE);pause.setVisibility(updates.downloading?View.VISIBLE:View.GONE);
        boolean job=release!=null&&updates.job!=null&&release.url.equals(updates.job.url);
        status.setText(updates.checking?updates.checkStatus:job&&!updates.downloadStatus.isEmpty()?updates.downloadStatus:updates.checkStatus);
        if(offered){
            download.setText(updates.ready&&job?"安装已下载 APK":job&&updates.received>0&&!updates.downloading?"继续下载 APK":"下载新版 APK · "+String.format(Locale.ROOT,"%.1f MB",release.size/1048576.0));
            detail.setText(release.tag+"\n\n"+(release.notes.isEmpty()?"发布来源：GitHub":release.notes));
        }else detail.setText("通过 GitHub 和镜像源下载更新，所有来源失败时可打开或复制发布链接。");
        boolean progress=job&&(starting||updates.downloading||updates.ready||updates.received>0);
        downloadProgress.setVisibility(progress?View.VISIBLE:View.GONE);
        if(progress){long received=Math.min(release.size,updates.received);int percent=(int)(received*100/release.size);
            downloadBar.setProgress(percent);downloadBar.setStateDescription(percent+"%");
            downloadPercent.setText(String.format(Locale.ROOT,"%d%% · %.1f / %.1f MB",percent,received/1048576.0,release.size/1048576.0));}
        if(updates.failureId!=seenFailure){seenFailure=updates.failureId;fallback(updates.error);}
    }
    private void download(){
        if(starting||updates.downloading||updates.checking||updates.installing)return;
        if(updates.ready){startActivity(new Intent(this,UpdateInstallActivity.class));return;}
        if(checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},72);return;
        }
        startDownload();
    }
    private void startDownload(){
        ApkRelease release=updates.offered();if(!updates.isNewer(release)||updates.downloading||starting)return;
        starting=true;render();
        try{startForegroundService(new Intent(this,UpdateDownloadService.class).putExtra("release",release.json()));}
        catch(RuntimeException e){starting=false;render();fallback("无法启动后台下载，请稍后重试。 "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==72){if(results.length==0||results[0]!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"通知未开启，下载完成后可返回应用安装",Toast.LENGTH_LONG).show();startDownload();}
    }
    private void fallback(String message){
        String url=updates.releasePage();new AlertDialog.Builder(this).setTitle("可以从 GitHub Release 下载")
            .setMessage((message==null?"下载暂不可用，可稍后继续。":message)+"\n\n"+url)
            .setPositiveButton("打开 Release",(d,w)->openRelease(url))
            .setNeutralButton("复制链接",(d,w)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("GitHub Release",url));Toast.makeText(this,"链接已复制",Toast.LENGTH_SHORT).show();})
            .setNegativeButton("关闭",null).show();
    }
    private void openRelease(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器，可复制链接后打开",Toast.LENGTH_LONG).show();}}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(LinearLayout page,String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8b9698,ACCENT}));
        b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff252e31,0xff1b272b}));
        b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(12);page.addView(b,p);return b;
    }
    @Override public void onStart(){super.onStart();updates.observe(observer);}
    @Override public void onStop(){updates.unobserve(observer);starting=false;super.onStop();}
    @Override public void onSaveInstanceState(Bundle out){out.putInt("seenFailure",seenFailure);super.onSaveInstanceState(out);}
}
