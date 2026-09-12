package io.github.sixzleo.tabfold.probe;

import android.content.AttributionSource;
import android.os.*;
import java.lang.reflect.Method;

/** Own process-bound state requests. Binder death releases overrides if the helper exits. */
public final class EarlyDisplayHelper {
    static Object global;
    static Method cancel,request;
    static volatile long lastPoll;
    static Object stateRequest(int state) throws Exception {
        Class<?> type=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Object builder=type.getMethod("newBuilder",int.class).invoke(null,state);
        return builder.getClass().getMethod("build").invoke(builder);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> g=Class.forName("android.hardware.devicestate.DeviceStateManagerGlobal");
        global=g.getMethod("getInstance").invoke(null);
        cancel=g.getMethod("cancelStateRequest");
        request=g.getMethod("requestState",Class.forName("android.hardware.devicestate.DeviceStateRequest"),
            java.util.concurrent.Executor.class,Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"));
        Runtime.getRuntime().addShutdownHook(new Thread(()->{try { cancel.invoke(global); }catch(Exception ignored) {}}));
        String filePrefix="/data/local/tmp/tabfold-projection-controller";
        java.io.RandomAccessFile lockFile=new java.io.RandomAccessFile(filePrefix+".lock","rw");
        java.nio.channels.FileLock lock=lockFile.getChannel().tryLock();
        if(lock==null) { System.out.println("ALREADY_RUNNING");System.exit(0); }
        java.io.File stop=new java.io.File(filePrefix+".stop");
        lastPoll=SystemClock.uptimeMillis();
        Thread watchdog=new Thread(()->{
            while(true) {
                try { Thread.sleep(500); } catch(InterruptedException e) { return; }
                // A blocked Binder call cannot leave an override active indefinitely.
                if(SystemClock.uptimeMillis()-lastPoll>3000) Runtime.getRuntime().halt(2);
            }
        },"TabFold-state-watchdog");
        watchdog.setDaemon(true);watchdog.start();
        String authority=args.length>1?args[1]:"io.github.sixzleo.tabfold.projection.surface";
        if(!authority.equals("io.github.sixzleo.tabfold.projection.surface"))
            throw new IllegalArgumentException("Unknown telemetry authority");
        Class<?> iam=Class.forName("android.app.IActivityManager");
        Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        IBinder token=new Binder();
        Object holder=iam.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class)
            .invoke(am,authority,0,token,"TabFoldEarlyDisplay");
        if(holder==null) throw new IllegalStateException("No telemetry provider");
        Object provider=holder.getClass().getField("provider").get(holder);
        Method call=Class.forName("android.content.IContentProvider").getMethod("call",AttributionSource.class,String.class,String.class,String.class,Bundle.class);
        AttributionSource source=new AttributionSource.Builder(2000).setPackageName("com.android.shell").build();
        EarlyDisplayModel model=new EarlyDisplayModel();int active=-1;
        int configuredOpen=-1,configuredClose=-1;
        long expires=args.length==0 || "0".equals(args[0])?Long.MAX_VALUE:SystemClock.uptimeMillis()+Integer.parseInt(args[0])*1000L;
        try {
            System.out.println("READY single-panel: opening >=60 inner, closing <=120 cover; reversal 3 degrees; <=3/>=175 release");
            while(SystemClock.uptimeMillis()<expires && !stop.exists()) {
                Bundle data=(Bundle)call.invoke(provider,source,authority,"desktop-telemetry",null,null);
                lastPoll=SystemClock.uptimeMillis();
                int open=data.getInt("openAngle",60),close=data.getInt("closeAngle",120);
                if(open!=configuredOpen||close!=configuredClose){model.configure(open,close);configuredOpen=open;configuredClose=close;System.out.println("SETTINGS opening="+open+" closing="+close);}
                boolean allowed=data.getBoolean("allowed") && SystemClock.uptimeMillis()-data.getLong("updatedAt")<1200;
                int next=model.update(data.getFloat("angle",Float.NaN),allowed,data.getBoolean("primaryInner"));
                if(next!=active) {
                    if(next<0) cancel.invoke(global);
                    else {
                        request.invoke(global,stateRequest(next),null,null);
                    }
                    System.out.println("STATE "+active+" -> "+next+" angle="+data.getFloat("angle")+" allowed="+allowed);
                    active=next;
                }
                Thread.sleep(40);
            }
        } finally {
            cancel.invoke(global);
            iam.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(am,authority,token,0);
            System.out.println("STOPPED override released");
            watchdog.interrupt();lock.release();lockFile.close();
        }
        System.exit(0);
    }
}
