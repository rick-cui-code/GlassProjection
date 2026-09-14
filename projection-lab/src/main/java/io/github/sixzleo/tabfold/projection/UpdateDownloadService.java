package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

/** User-started download survives leaving/rotating settings; no background polling. */
public final class UpdateDownloadService extends Service {
    static final int ID=59;
    private static final String CHANNEL="apk_download",READY_CHANNEL="apk_download_ready";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private UpdateCoordinator updates;
    private ResumableUpdate transfer;
    private NotificationManager notifications;
    private PowerManager.WakeLock wake;
    private boolean running,destroyed;
    @Override public void onCreate(){
        super.onCreate();updates=UpdateCoordinator.get(this);notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"APK 下载进度",NotificationManager.IMPORTANCE_LOW));
        notifications.createNotificationChannel(new NotificationChannel(READY_CHANNEL,"APK 下载完成",NotificationManager.IMPORTANCE_DEFAULT));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"pause".equals(intent.getAction())){
            if(transfer!=null)transfer.cancel();else stopSelf();return START_NOT_STICKY;
        }
        if(running)return START_NOT_STICKY;
        startForeground(ID,notification(false,"正在准备 APK 下载"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        try{
            String json=intent==null?null:intent.getStringExtra("release");
            ApkRelease release=json==null?updates.job:new ApkRelease(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!updates.isNewer(release))throw new IOException("没有可下载的新版本");
            updates.begin(release);running=true;transfer=new ResumableUpdate();
            wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GlassProjection:apkDownload");wake.acquire(10*60*1000L);
            worker.execute(()->{
                try{
                    transfer.download(release.url,updates.apk(),release.size,release.hash,file->updates.verifyApk(file,release),new UpdateTransport.Progress(){
                        public void show(String message){main.post(()->{if(!destroyed){updates.progress(message);refresh();}});}
                        public void bytes(long value,long total){main.post(()->{if(!destroyed){updates.bytes(value);refresh();}});}
                    });
                    main.post(()->finish(null));
                }catch(Exception error){main.post(()->finish(error));}
            });
        }catch(Exception error){finish(error);}
        return START_NOT_STICKY;
    }
    private void refresh(){if(running)notifications.notify(ID,notification(false,updates.downloadStatus));}
    private void finish(Exception error){
        if(destroyed)return;running=false;stopForeground(STOP_FOREGROUND_REMOVE);
        if(error==null){updates.completed();notifications.notify(ID,notification(true,"下载完成，点击安装"));updates.tryAutoInstall(true);}
        else {updates.failed(error);notifications.notify(ID,notification(false,updates.downloadStatus));}
        stopSelf();
    }
    private Notification notification(boolean ready,String message){
        Intent screen=new Intent(this,ready?UpdateInstallActivity.class:UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open=PendingIntent.getActivity(this,ready?60:59,screen,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,ready?READY_CHANNEL:CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(ready?"玻璃投影更新已就绪":"玻璃投影 APK 下载")
            .setContentText(message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(running).setAutoCancel(!running);
        if(running){
            long total=updates.job==null?0:updates.job.size;
            builder.setProgress(100,total>0?(int)Math.min(100,updates.received*100/total):0,total<=0);
            PendingIntent pause=PendingIntent.getService(this,59,new Intent(this,UpdateDownloadService.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null,"暂停",pause).build());
        }
        return builder.build();
    }
    @Override public void onTimeout(int startId,int fgsType){if(transfer!=null)transfer.cancel();finish(new InterruptedIOException("下载已暂停，进度已保存"));}
    @Override public void onDestroy(){
        destroyed=true;if(transfer!=null)transfer.cancel();worker.shutdownNow();
        if(running){running=false;updates.failed(new InterruptedIOException("下载已暂停，进度已保存"));}
        if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
