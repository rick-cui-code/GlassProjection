package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.os.*;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Discovers this phone only; all ADB connections use loopback, never remote hosts. */
final class WirelessAdb {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final AtomicBoolean busy=new AtomicBoolean();
    private static long nextAttempt;
    static boolean paired(Context c){return c.getSharedPreferences("wireless",0).getBoolean("paired",false);}
    static boolean isConnecting(){return busy.get();}
    static void reconnect(Context context){
        if(!paired(context)||SystemClock.uptimeMillis()<nextAttempt)return;
        nextAttempt=SystemClock.uptimeMillis()+30000;connect(context,null);
    }
    static void connect(Context context,java.util.function.Consumer<Boolean> done){
        if(MobileHelper.wirelessReady()){if(done!=null)done.accept(true);return;}
        if(!busy.compareAndSet(false,true)){if(done!=null)done.accept(false);return;}
        Context app=context.getApplicationContext();
        worker.execute(()->{
            boolean success=false;
            String stage="discovery";
            try{
                MobileHelper.message="正在自动查找本机无线调试";
                int port=discover(app,AdbMdns.SERVICE_TYPE_TLS_CONNECT,10000);
                if(port<=0)throw new IOException("Discovery unavailable");
                stage="authentication";
                LocalAdbIdentity adb=new LocalAdbIdentity(app);
                try{
                    MobileHelper.message="正在连接本机";
                    if(!adb.connect(port))throw new IOException("Connection unavailable");
                    android.util.Log.i("GlassWireless","Local ADB authenticated");
                    MobileHelper.selectWireless(app);
                    stage="host launch";
                    // Only the installed APK entry point can be launched. No user-supplied shell text.
                    String apk=app.getApplicationInfo().sourceDir;
                    // Keep the launching shell alive until the Binder handshake. In legacy ADB
                    // shells, exiting immediately after '&' can kill the child before it detaches.
                    String command="CLASSPATH="+quote(apk)+" setsid -w nohup /system/bin/app_process /system/bin io.github.sixzleo.tabfold.projection.StandaloneHelper </dev/null >/data/local/tmp/glass-standalone.log 2>&1";
                    try(AdbStream stream=adb.openStream("shell:"+command)){
                        stage="host handshake";
                        long deadline=SystemClock.uptimeMillis()+12000;
                        while(!MobileHelper.wirelessReady()&&!stream.isClosed()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(50);
                        success=MobileHelper.wirelessReady();
                    }
                    android.util.Log.i("GlassWireless",success?"Standalone host connected":"Standalone host handshake timed out");
                    if(!success)MobileHelper.message="配对已保存，但助手启动失败。请返回应用点「配对」重试连接";
                }finally{adb.disconnect();}
            }catch(Exception error){
                if(error instanceof io.github.muntashirakon.adb.AdbPairingRequiredException){
                    app.getSharedPreferences("wireless",0).edit().putBoolean("paired",false).apply();
                    MobileHelper.message="系统配对授权已失效，请点「配对」重新连接";
                }else MobileHelper.message=paired(app)?"未能自动连接，请开启无线调试；授权被移除时需重新配对":"请先完成一次无线配对";
                android.util.Log.w("GlassWireless","Connection failed at "+stage+": "+error.getClass().getSimpleName());
            }finally{
                busy.set(false);final boolean result=success;if(done!=null)main.post(()->done.accept(result));
            }
        });
    }
    static void pair(Context context,int port,String code,java.util.function.Consumer<Boolean> done){
        if(port<=0||port>65535||code==null||!code.matches("[0-9]{6}")||!busy.compareAndSet(false,true)){done.accept(false);return;}
        Context app=context.getApplicationContext();
        worker.execute(()->{
            boolean success=false;
            try{
                LocalAdbIdentity adb=new LocalAdbIdentity(app);
                try{success=adb.pair("127.0.0.1",port,code);}finally{adb.disconnect();}
                if(success)app.getSharedPreferences("wireless",0).edit().putBoolean("paired",true).apply();
            }catch(Exception error){android.util.Log.w("GlassWireless","Pairing failed: "+error.getClass().getSimpleName());}
            finally{busy.set(false);boolean result=success;main.post(()->done.accept(result));}
        });
    }
    private static int discover(Context app,String type,long timeout)throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger result=new java.util.concurrent.atomic.AtomicInteger(-1);
        CountDownLatch found=new CountDownLatch(1);
        AdbMdns mdns=new AdbMdns(app,type,(host,port)->{if(port>0){result.set(port);found.countDown();}});
        main.post(()->{try{mdns.start();}catch(RuntimeException error){found.countDown();}});
        try{found.await(timeout,TimeUnit.MILLISECONDS);return result.get();}
        finally{main.post(()->{try{mdns.stop();}catch(RuntimeException ignored){}});}
    }
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
