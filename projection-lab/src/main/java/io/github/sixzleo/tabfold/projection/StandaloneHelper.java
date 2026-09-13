package io.github.sixzleo.tabfold.projection;

import android.content.*;
import android.os.*;
import java.io.*;
import java.nio.channels.FileLock;

/** ADB launches this APK entry point; only our app receives its Binder. */
public final class StandaloneHelper {
    public static void main(String[] args)throws Exception {
        System.out.println("START standalone host");
        if(android.os.Process.myUid()!=2000)throw new SecurityException("ADB shell required");
        try(RandomAccessFile file=new RandomAccessFile("/data/local/tmp/glass-standalone.lock","rw");
            FileLock lock=file.getChannel().tryLock()){
            if(lock==null){System.out.println("EXIT another host holds the lock");return;}
            Looper.prepareMainLooper();
            Class<?> threadClass=Class.forName("android.app.ActivityThread");
            Object thread=threadClass.getMethod("systemMain").invoke(null);
            Context system=(Context)threadClass.getMethod("getSystemContext").invoke(thread);
            Context app=system.createPackageContext("io.github.sixzleo.tabfold.projection",Context.CONTEXT_IGNORE_SECURITY);
            MobileHelperHost host=new MobileHelperHost(app);
            System.out.println("PREPARED bundled helpers");
            Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            IBinder token=new Binder();String authority="io.github.sixzleo.tabfold.projection.surface";
            Object holder=Class.forName("android.app.IActivityManager").getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
                .invoke(am,authority,0,token,"GlassStandalone");
            if(holder==null)throw new IOException("Application unavailable");
            Object provider=holder.getClass().getField("provider").get(holder);
            java.lang.reflect.Method call=Class.forName("android.content.IContentProvider").getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class);
            Bundle extras=new Bundle();extras.putBinder("host",host);
            extras.putLong("version",app.getPackageManager().getPackageInfo(app.getPackageName(),0).getLongVersionCode());
            System.out.println("CONNECT version="+extras.getLong("version"));
            Handler handler=new Handler(Looper.getMainLooper());
            handler.post(new Runnable(){private boolean announced;public void run(){
                try{
                    Bundle result=(Bundle)call.invoke(provider,new AttributionSource.Builder(2000).setPackageName("com.android.shell").build(),authority,"helper-connect",null,extras);
                    if(result==null||!result.getBoolean("accepted")){System.out.println("EXIT application rejected host");host.destroy();return;}
                    if(!announced){System.out.println("READY application accepted host");announced=true;}
                    handler.postDelayed(this,3000);
                }catch(Exception error){System.out.println("EXIT host connection failed");error.printStackTrace(System.out);host.destroy();}
            }});
            Looper.loop();
        }catch(Throwable error){System.out.println("FAILED standalone startup");error.printStackTrace(System.out);throw error;}
    }
}
