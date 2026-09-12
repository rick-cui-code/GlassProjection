package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.file.*;

/** Shizuku owns this shell-UID process. It starts only our bundled, unchanged helpers. */
public final class MobileHelperHost extends IHelperHost.Stub {
    private final int appUid;
    private java.lang.Process renderer,controller;
    private boolean running;
    private static final String ROOT="/data/local/tmp/";
    public MobileHelperHost(Context context)throws IOException {
        appUid=context.getApplicationInfo().uid;
        if(android.os.Process.myUid()!=2000)throw new SecurityException("This build requires Shizuku in ADB mode");
        // Take over an earlier ADB-started instance using its existing stop protocol.
        new File(ROOT+"tabfold-live.stop").createNewFile();new File(ROOT+"tabfold-projection-controller.stop").createNewFile();
        SystemClock.sleep(1200);
        install(context,"live.dex","tabfold-continuous-probe.dex");
        install(context,"controller.dex","tabfold-projection-controller.dex");
    }
    private void caller(){int uid=Binder.getCallingUid();if(uid!=appUid&&uid!=2000&&uid!=0)throw new SecurityException("Own app only");}
    private static void install(Context c,String asset,String name)throws IOException {
        File target=new File(ROOT+name),pending=new File(ROOT+name+".mobile-pending");
        Files.deleteIfExists(pending.toPath());
        try(InputStream in=c.getAssets().open("helpers/"+asset);OutputStream out=new FileOutputStream(pending)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        // Android requires dynamically loaded code to be read-only.
        if(!pending.setReadOnly())throw new IOException("Cannot protect bundled helper");
        Files.move(pending.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    private static java.lang.Process start(String dex,String main,String log,String... args)throws IOException {
        java.util.ArrayList<String> command=new java.util.ArrayList<>();command.add("/system/bin/app_process");command.add("/system/bin");command.add(main);java.util.Collections.addAll(command,args);
        ProcessBuilder p=new ProcessBuilder(command);p.environment().put("CLASSPATH",ROOT+dex);
        p.redirectInput(new File("/dev/null"));p.redirectErrorStream(true);p.redirectOutput(new File(ROOT+log));return p.start();
    }
    @Override public synchronized int ensureRunning(){
        caller();running=true;
        try {
            new File(ROOT+"tabfold-live.stop").delete();new File(ROOT+"tabfold-projection-controller.stop").delete();
            if(controller==null||!controller.isAlive())controller=start("tabfold-projection-controller.dex","io.github.sixzleo.tabfold.probe.EarlyDisplayHelper","tabfold-projection-controller.log","0","io.github.sixzleo.tabfold.projection.surface");
            if(renderer==null||!renderer.isAlive())renderer=start("tabfold-continuous-probe.dex","io.github.sixzleo.tabfold.probe.LiveMirrorWindowProbe","tabfold-live.log","live");
            return (renderer.isAlive()?1:0)|(controller.isAlive()?2:0);
        }catch(IOException e){android.util.Log.e("MobileHelperHost","start",e);return 0;}
    }
    @Override public synchronized void stopHelpers(){caller();stop();}
    private void stop(){
        running=false;
        try{new File(ROOT+"tabfold-live.stop").createNewFile();new File(ROOT+"tabfold-projection-controller.stop").createNewFile();}catch(IOException ignored){}
        if(renderer!=null)renderer.destroy();if(controller!=null)controller.destroy();renderer=null;controller=null;
    }
    @Override public synchronized String status(){caller();return "uid="+android.os.Process.myUid()+" requested="+running+" renderer="+(renderer!=null&&renderer.isAlive())+" controller="+(controller!=null&&controller.isAlive());}
    @Override public synchronized void destroy(){caller();stop();System.exit(0);}
}
