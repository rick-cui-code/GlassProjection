package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.*;

/** Own visible installer entry, also the direct target of the completion notification. */
public final class UpdateInstallActivity extends Activity {
    private UpdateCoordinator updates;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;
    private boolean busy,ownsInstall,installerLaunched;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);updates=UpdateCoordinator.get(this);
        if(updates.installing){finish();return;}
        ownsInstall=true;updates.installOpened();getSystemService(NotificationManager.class).cancel(UpdateDownloadService.ID);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(48,80,48,48);page.setBackgroundColor(0xff10191c);setContentView(page);
        status=new TextView(this);status.setTextColor(0xffedf4f3);status.setTextSize(18);page.addView(status);
        Button retry=new Button(this);retry.setText("继续安装");retry.setOnClickListener(v->install());page.addView(retry);
        Button close=new Button(this);close.setText("稍后安装");close.setOnClickListener(v->finish());page.addView(close);
        install();
    }
    private void install(){
        if(busy)return;
        if(!updates.ready||updates.job==null){status.setText("没有可安装的 APK，请返回检查更新。");return;}
        if(!getPackageManager().canRequestPackageInstalls()){
            status.setText("请允许玻璃投影安装应用，返回后继续安装。");
            try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),61);}
            catch(ActivityNotFoundException e){status.setText("请在应用信息中允许安装应用，再点击继续安装。");}return;
        }
        busy=true;status.setText("正在验证 APK");
        worker.execute(()->{
            try{updates.verifyApk(updates.apk(),updates.job);main.post(()->{
                if(isDestroyed())return;status.setText("正在准备安装，保留无障碍开关");ProjectionService.pauseForUpdate();
                main.postDelayed(new Runnable(){int checks;public void run(){
                    if(isDestroyed())return;
                    if(MobileHelper.ready()){
                        if(++checks<30){main.postDelayed(this,100);return;}
                        ProjectionService.resumeAfterUpdate();busy=false;status.setText("助手仍在停止，请稍后点击继续安装。");return;
                    }
                    try{
                        Uri uri=Uri.parse("content://"+getPackageName()+".updates/verified.apk");
                        installerLaunched=true;startActivityForResult(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true),62);
                    }catch(ActivityNotFoundException e){installerLaunched=false;ProjectionService.resumeAfterUpdate();busy=false;status.setText("无法打开系统安装程序，请从 Release 下载。");}
                }},400);
            });}catch(Exception error){main.post(()->{if(!isDestroyed()){busy=false;status.setText(error.getMessage());}});}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==61){if(getPackageManager().canRequestPackageInstalls())install();}
        else if(request==62){ProjectionService.resumeAfterUpdate();finish();}
    }
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);worker.shutdownNow();if(ownsInstall){if(!installerLaunched)ProjectionService.resumeAfterUpdate();updates.installClosed();}super.onDestroy();}
}
