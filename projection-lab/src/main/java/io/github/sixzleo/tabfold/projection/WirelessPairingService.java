package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import io.github.muntashirakon.adb.android.AdbMdns;

/** A short, user-started pairing session using notification inline replies. */
public final class WirelessPairingService extends Service {
    static final String CHANNEL="wireless_pairing";
    private static final String CODE="pairing_code";
    private static final int ID=58;
    private final Handler main=new Handler(Looper.getMainLooper());
    private AdbMdns discovery;
    private int port;
    private boolean working,closed;
    private NotificationManager notifications;
    @Override public void onCreate(){
        super.onCreate();MobileHelper.init(this);
        notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,"本机无线配对",NotificationManager.IMPORTANCE_HIGH));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"cancel".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(discovery==null){
            Notification first=notification("等待系统配对窗口","打开无线调试，点「使用配对码配对设备」。无需抄写地址和端口。",false);
            if(Build.VERSION.SDK_INT>=34)startForeground(ID,first,ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);else startForeground(ID,first);
            discovery=new AdbMdns(this,AdbMdns.SERVICE_TYPE_TLS_PAIRING,(host,value)->main.post(()->{
                if(closed)return;port=value;
                if(!working)show(port>0?"已找到本机配对窗口":"等待系统配对窗口",port>0?"下拉通知栏，点「输入配对码」，填写系统显示的 6 位码；保持系统配对窗口打开。":"打开无线调试，点「使用配对码配对设备」。",port>0);
            }));
            discovery.start();main.postDelayed(this::stopSelf,150000);
        }
        if(intent!=null&&"pair".equals(intent.getAction())&&!working){
            Bundle input=RemoteInput.getResultsFromIntent(intent);
            CharSequence code=input==null?null:input.getCharSequence(CODE);
            if(code==null||!code.toString().trim().matches("[0-9]{6}")||port<=0){show("请检查配对窗口","需要当前窗口的 6 位配对码；关闭窗口后请重新打开。",port>0);return START_NOT_STICKY;}
            submit(code.toString().trim());
        }
        return START_NOT_STICKY;
    }
    private void submit(String code){
        if(working)return;
        if(port<=0||!code.matches("[0-9]{6}")){show("请检查配对窗口","先打开系统配对窗口，再输入当前 6 位码。",port>0);return;}
        working=true;show("正在配对","成功后会自动连接并启动助手。",false);
        WirelessAdb.pair(this,port,code,success->{
                if(closed)return;
                if(!success){working=false;show("配对未成功","请确认窗口仍打开，并重新输入当前配对码。",port>0);return;}
                show("配对成功，正在连接","正在自动发现连接端口并启动助手。",false);
                WirelessAdb.connect(this,connected->{
                    if(closed)return;
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    notifications.notify(ID,notification(connected?"助手已连接":"配对已保存",connected?"点击返回玻璃投影，继续开启无障碍。":"请返回玻璃投影重试自动连接。",false).clone());
                    Toast.makeText(this,connected?"助手已连接，请返回玻璃投影":"配对已保存，请返回应用重试连接",Toast.LENGTH_LONG).show();
                    stopSelf();
                });
            });
    }
    private void show(String title,String text,boolean input){
        MobileHelper.message=title;notifications.notify(ID,notification(title,text,input));
    }
    private Notification notification(String title,String text,boolean input){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,DesktopActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true);
        if(input){
            PendingIntent reply=PendingIntent.getService(this,1,new Intent(this,WirelessPairingService.class).setAction("pair"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
            b.addAction(new Notification.Action.Builder(null,"输入配对码",reply).addRemoteInput(new RemoteInput.Builder(CODE).setLabel("6 位配对码").build()).build());
        }else if(!working){
            Intent settings=new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS");
            if(settings.resolveActivity(getPackageManager())==null)settings=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
            b.addAction(new Notification.Action.Builder(null,"打开无线调试",PendingIntent.getActivity(this,2,settings,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());
        }
        b.addAction(new Notification.Action.Builder(null,"结束",PendingIntent.getService(this,3,new Intent(this,WirelessPairingService.class).setAction("cancel"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());
        return b.build();
    }
    @Override public void onTimeout(int startId,int fgsType){stopSelf();}
    @Override public void onDestroy(){closed=true;main.removeCallbacksAndMessages(null);if(discovery!=null)discovery.stop();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
