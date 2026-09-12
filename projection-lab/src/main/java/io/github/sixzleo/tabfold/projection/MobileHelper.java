package io.github.sixzleo.tabfold.projection;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;
import java.util.concurrent.*;

/** Lifecycle belongs to the accessibility service, never to its settings Activity. */
final class MobileHelper {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static Context context;
    private static volatile IHelperHost host;
    private static volatile boolean active;
    private static boolean initialized,binding;
    private static long bindingAt;
    static volatile String message="等待 Shizuku";
    private static Shizuku.UserServiceArgs arguments;
    private static final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){binding=false;host=IHelperHost.Stub.asInterface(binder);message="手机端助手已连接";schedule();}
        public void onServiceDisconnected(ComponentName name){binding=false;host=null;message="助手连接中断，等待恢复";}
    };
    static void init(Context c){
        if(initialized)return;initialized=true;context=c.getApplicationContext();
        arguments=new Shizuku.UserServiceArgs(new ComponentName(context,MobileHelperHost.class)).daemon(true).processNameSuffix("glass_helpers").tag("glass_helpers").version(1);
        Shizuku.addBinderReceivedListenerSticky(()->{message="Shizuku 已启动";schedule();});
        Shizuku.addBinderDeadListener(()->{host=null;binding=false;message="Shizuku 已停止，请在手机上重新启动";});
        Shizuku.addRequestPermissionResultListener((code,result)->{if(code==312){message=result==PackageManager.PERMISSION_GRANTED?"已授权，正在连接":"未授予 Shizuku 权限";schedule();}});
    }
    static void start(Context c){init(c);active=true;schedule();}
    private static void schedule(){main.removeCallbacks(tick);main.post(tick);}
    private static final Runnable tick=new Runnable(){public void run(){
        if(!active)return;
        try{
            if(!Shizuku.pingBinder())message="请先在手机上启动 Shizuku";
            else if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)message="请授权 Shizuku 启动助手";
            else if(host==null){
                if(!binding||SystemClock.uptimeMillis()-bindingAt>10000){binding=true;bindingAt=SystemClock.uptimeMillis();Shizuku.bindUserService(arguments,connection);message="正在启动手机端助手";}
            }else{
                final IHelperHost current=host;
                worker.execute(()->{if(!active)return;try{int state=current.ensureRunning();message=state==3?"手机端助手运行中":"助手启动未完成";}catch(Exception e){host=null;binding=false;message="连接中断，正在重连";}});
            }
        }catch(RuntimeException e){binding=false;message="Shizuku 连接失败，请检查授权";}
        main.postDelayed(this,3000);
    }};
    static void stop(){
        active=false;main.removeCallbacks(tick);final IHelperHost current=host;
        worker.execute(()->{if(active)return;try{if(current!=null)current.stopHelpers();}catch(Exception ignored){}
            main.post(()->{if(active)return;try{if(arguments!=null&&Shizuku.pingBinder())Shizuku.unbindUserService(arguments,connection,true);}catch(RuntimeException ignored){}host=null;binding=false;});
        });
    }
    static void authorize(Context c){
        init(c);
        try{
            if(Shizuku.pingBinder()){
                if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED)Shizuku.requestPermission(312);
                else {message="Shizuku 已授权";schedule();}
            }else{
                Intent launch=c.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if(launch!=null)c.startActivity(launch);else message="请先安装 Shizuku";
            }
        }catch(RuntimeException e){message="请在 Shizuku 中检查本应用授权";}
    }
}
