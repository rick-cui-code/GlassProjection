package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Manual GitHub APK updates with mirror fallback and the normal Android installer. */
public final class UpdateActivity extends Activity {
    private static final int BG=0xff10191c,TEXT=0xffedf4f3,MUTED=0xffa6b9bb,ACCENT=0xffa4e6d6;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private UpdateTransport transfer;
    private ApkRelease release;
    private TextView status,detail,downloadPercent;
    private LinearLayout downloadProgress;
    private ProgressBar downloadBar;
    private final UpdateTransport.Progress downloadListener=new UpdateTransport.Progress(){
        public void show(String message){progress(message);}
        public void bytes(long received,long total){runOnUiThread(()->{
            if(!isDestroyed())showDownloadProgress(received,total);
        });}
    };
    private Button check,download,cancel;
    private File folder,downloaded;
    private boolean busy,waitingPermission;
    private String currentVersion,releaseUrl=UpdateTrust.RELEASES;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);folder=new File(getCacheDir(),"updates");folder.mkdirs();
        try{currentVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){currentVersion="0.0.0";}
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bar=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bar.left,bar.top,bar.right,bar.bottom);return insets;});
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(page);
        button(page,"返回",this::finish);page.addView(text("检查更新",27,TEXT));page.addView(text("当前版本 "+currentVersion,14,MUTED));
        status=text("从 GitHub 检查最新 APK",17,ACCENT);status.setPadding(0,dp(24),0,dp(12));page.addView(status);
        detail=text("下载会依次尝试 GitHub、ghfast.top、gh-proxy.com、ghproxy.net。全部失败时可打开或复制 Release 链接。",14,MUTED);page.addView(detail);
        check=button(page,"检查更新",this::check);
        download=button(page,"下载新版 APK",this::download);download.setVisibility(View.GONE);
        downloadProgress=new LinearLayout(this);downloadProgress.setOrientation(LinearLayout.VERTICAL);
        downloadProgress.setPadding(0,dp(8),0,dp(4));downloadProgress.setVisibility(View.GONE);
        page.addView(downloadProgress,new LinearLayout.LayoutParams(-1,-2));
        downloadPercent=text("",13,MUTED);downloadPercent.setGravity(Gravity.END);
        downloadProgress.addView(downloadPercent,new LinearLayout.LayoutParams(-1,-2));
        downloadBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        downloadBar.setMax(100);downloadBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        downloadBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff34474b));
        LinearLayout.LayoutParams barLayout=new LinearLayout.LayoutParams(-1,dp(8));barLayout.topMargin=dp(6);
        downloadProgress.addView(downloadBar,barLayout);
        cancel=button(page,"取消下载",()->{if(transfer!=null)transfer.cancel();});cancel.setVisibility(View.GONE);
        button(page,"打开 GitHub Release",()->openRelease(releaseUrl));
        page.addView(text("仅在本页检查和下载。下载完成后由系统确认安装；安装前暂停动画，完成后请重新开启无障碍服务。",12,MUTED));
        if(saved!=null)try{
            String tag=saved.getString("releaseTag");if(tag!=null){ApkRelease cached=new ApkRelease(Files.readAllBytes(new File(folder,"release.json").toPath()));
                if(tag.equals(cached.tag)){release=cached;releaseUrl=release.page;detail.setText(release.notes);download.setVisibility(View.VISIBLE);}}
            File apk=new File(folder,"verified.apk");if(saved.getBoolean("downloaded")&&release!=null&&apk.isFile()){downloaded=apk;download.setText("安装已下载 APK");showDownloadProgress(release.size,release.size);}
            waitingPermission=saved.getBoolean("waitingPermission");
        }catch(Exception ignored){}
        scroll.requestApplyInsets();
    }
    private void setBusy(boolean value){busy=value;check.setEnabled(!value);download.setEnabled(!value);download.setAlpha(value?.55f:1f);cancel.setVisibility(value&&transfer!=null?View.VISIBLE:View.GONE);}
    private void progress(String message){runOnUiThread(()->{if(!isDestroyed())status.setText(message);});}
    private void showDownloadProgress(long received,long total){
        int percent=total>0?(int)Math.min(100,Math.max(0,received*100/total)):0;
        downloadProgress.setVisibility(View.VISIBLE);downloadBar.setProgress(percent);
        downloadBar.setStateDescription(percent+"%");
        downloadPercent.setText(String.format(Locale.ROOT,"%d%% · %.1f / %.1f MB",percent,received/1048576.0,total/1048576.0));
    }
    private void check(){
        if(busy)return;release=null;downloaded=null;releaseUrl=UpdateTrust.RELEASES;download.setVisibility(View.GONE);downloadProgress.setVisibility(View.GONE);
        transfer=new UpdateTransport();setBusy(true);
        worker.execute(()->{
            try{
                final ApkRelease[] found=new ApkRelease[1];
                transfer.download(UpdateTrust.API,new File(folder,"release.json"),-1,1024*1024,
                    file->found[0]=new ApkRelease(Files.readAllBytes(file.toPath())),this::progress);
                ApkRelease next=found[0];boolean newer=UpdateTrust.compareVersion(next.tag,currentVersion)>0;
                runOnUiThread(()->{if(isDestroyed())return;release=next;releaseUrl=next.page;setBusy(false);
                    status.setText(newer?"发现新版本 "+next.tag:"暂无新版 · GitHub 最新 "+next.tag);
                    detail.setText(next.notes.isEmpty()?"发布来源：GitHub":next.notes);
                    download.setText("下载新版 APK · "+String.format(Locale.ROOT,"%.1f MB",next.size/1048576.0));download.setVisibility(newer?View.VISIBLE:View.GONE);
                });
            }catch(Exception error){failed(error);}
        });
    }
    private void download(){
        if(busy||release==null)return;if(downloaded!=null){requestInstall();return;}
        final ApkRelease next=release;transfer=new UpdateTransport();setBusy(true);showDownloadProgress(0,next.size);
        worker.execute(()->{
            try{
                File file=transfer.download(next.url,new File(folder,"verified.apk"),next.size,UpdateTrust.MAX_APK,
                    candidate->verifyApk(candidate,next),downloadListener);
                runOnUiThread(()->{if(isDestroyed())return;downloaded=file;setBusy(false);status.setText("APK 已下载，校验通过");download.setText("安装已下载 APK");requestInstall();});
            }catch(Exception error){failed(error);}
        });
    }
    private void verifyApk(File apk,ApkRelease next)throws Exception {
        UpdateTrust.checkFile(apk,next.size,next.hash);
        ApkVerifier.Result verified=new ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(33).build().verify();
        if(!verified.isVerified())throw new IOException("APK 签名验证失败");
        PackageManager pm=getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(apk.getPath(),0);
        PackageInfo installed=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!getPackageName().equals(archive.packageName)||archive.getLongVersionCode()<=installed.getLongVersionCode()
            ||UpdateTrust.compareVersion(archive.versionName,next.tag)!=0)throw new IOException("APK 包名或版本与更新不符");
        if(installed.signingInfo==null)throw new IOException("无法读取当前应用签名");
        Set<String> old=new HashSet<>(),fresh=new HashSet<>();
        for(android.content.pm.Signature value:installed.signingInfo.getApkContentsSigners())old.add(Base64.getEncoder().encodeToString(value.toByteArray()));
        for(java.security.cert.X509Certificate cert:verified.getSignerCertificates())fresh.add(Base64.getEncoder().encodeToString(cert.getEncoded()));
        if(old.isEmpty()||!old.equals(fresh))throw new IOException("新版签名与当前版本不同，无法覆盖安装");
    }
    private void requestInstall(){
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this).setTitle("允许安装更新").setMessage("请允许玻璃投影安装应用，返回后继续安装已下载的 APK。")
                .setPositiveButton("前往设置",(d,w)->{waitingPermission=true;try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){waitingPermission=false;fallback("请在系统设置中允许安装应用。",releaseUrl);}})
                .setNegativeButton("稍后",null).show();return;
        }
        transfer=null;setBusy(true);status.setText("正在准备安装");
        worker.execute(()->{try{
            verifyApk(downloaded,release);
            runOnUiThread(()->{if(isDestroyed())return;ProjectionService.stop();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable(){int checks;public void run(){
                    if(isDestroyed())return;
                    if(ProjectionService.instance!=null){if(++checks<20){new Handler(Looper.getMainLooper()).postDelayed(this,100);return;}setBusy(false);status.setText("请先暂停动画，再点击安装");return;}
                    setBusy(false);status.setText("请完成系统安装，之后重新开启无障碍服务");
                    Uri uri=Uri.parse("content://"+getPackageName()+".updates/verified.apk");
                    try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
                    catch(ActivityNotFoundException e){fallback("无法打开系统安装程序。",releaseUrl);}
                }},400);
            });
        }catch(Exception error){failed(error);}});
    }
    private void failed(Exception error){runOnUiThread(()->{
        if(isDestroyed())return;setBusy(false);downloadProgress.setVisibility(View.GONE);
        if(error instanceof InterruptedIOException){status.setText("已取消更新");return;}
        status.setText("更新未完成，当前版本保持可用");fallback(error.getMessage(),releaseUrl);
    });}
    private void fallback(String message,String url){
        new AlertDialog.Builder(this).setTitle("可以从 GitHub Release 下载").setMessage((message==null?"所有下载源均不可用。":message)+"\n\n"+url)
            .setPositiveButton("打开 Release",(d,w)->openRelease(url))
            .setNeutralButton("复制链接",(d,w)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("GitHub Release",url));Toast.makeText(this,"链接已复制",Toast.LENGTH_SHORT).show();})
            .setNegativeButton("关闭",null).show();
    }
    private void openRelease(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){Toast.makeText(this,"未找到浏览器，可复制链接后打开",Toast.LENGTH_LONG).show();}}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(LinearLayout page,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8b9698,ACCENT}));b.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff252e31,0xff1b272b}));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.topMargin=dp(12);page.addView(b,p);return b;}
    @Override public void onResume(){super.onResume();if(waitingPermission){waitingPermission=false;if(getPackageManager().canRequestPackageInstalls()&&downloaded!=null)requestInstall();}}
    @Override public void onSaveInstanceState(Bundle out){
        out.putBoolean("waitingPermission",waitingPermission);out.putBoolean("downloaded",downloaded!=null);
        if(release!=null)out.putString("releaseTag",release.tag);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroy(){if(transfer!=null)transfer.cancel();worker.shutdownNow();super.onDestroy();}
}
