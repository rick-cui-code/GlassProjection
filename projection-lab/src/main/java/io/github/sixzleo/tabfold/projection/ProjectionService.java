package io.github.sixzleo.tabfold.projection;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.accessibility.*;
import android.widget.FrameLayout;
import java.util.HashSet;
import java.util.concurrent.*;

public final class ProjectionService extends AccessibilityService implements SensorEventListener {
    static volatile ProjectionService instance;
    static volatile long updatedAt,helperAt;
    static volatile boolean allowed,primaryInner,lockScreen,standby;
    static volatile FoldPose foldPose=new FoldPose(false);
    private float hinge=Float.NaN;
    static volatile String status="未开启";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HashSet<String> homes=new HashSet<>();
    private final FrameGate gate=new FrameGate();
    private final LockScreenGate lockGate=new LockScreenGate();
    private final FoldHoldGate holdGate=new FoldHoldGate();
    private volatile boolean foldHeld;
    private final TouchObservation touchObservation=new TouchObservation(this);
    private final FingerSwipeGate fingerSwipe=new FingerSwipeGate();
    private SensorManager sensors;
    private Sensor physicalFoldSensor;
    private DisplayManager displays;
    private WindowManager manager;
    private FrameLayout window;
    private boolean connected,pending,homeUncertain;
    private String scene="";
    private long retryAt,lastHomeAt,shownSignature;
    private int generation,attempts;
    private DesktopProjection overlay;
    private volatile MirrorPreview mirrorPreview;
    private volatile long mirrorUntil;
    private boolean livePreferred;
    private long liveUntil;
    private long mirrorObserveUntil;
    private String mirrorScene="";
    private final ProjectionSceneTiming entryTiming=new ProjectionSceneTiming();
    private long entrySceneStarted(int width,int height,int rotation,boolean inner){
        return entryTiming.observe(width,height,rotation,inner,SystemClock.uptimeMillis());
    }
    private boolean mirrorFold;
    private final ScreenFade screenFade=new ScreenFade();
    private volatile long coverToken,coverStarted;
    private volatile boolean coverBacking,screenFadeActive;
    private volatile float screenDarkness,screenFadeFrom;
    private volatile long screenFadeAt;
    private volatile int screenFadeMode;
    static void prepareBlackout(long id){ProjectionService s=instance;if(s!=null)s.main.post(()->{
        if(s.mirrorPreview!=null){s.mirrorPreview.prepareBlackout(id);s.mirrorPreview.setBlackout(s.coverBacking);}
    });}
    static void markCoverReady(String request){ProjectionService s=instance;if(s!=null)s.main.post(()->{
        if(request==null)return;
        int split=request.indexOf(':');if(split<0)return;
        long token;try{token=Long.parseLong(request.substring(0,split));}catch(NumberFormatException e){return;}
        Display d=s.displays.getDisplay(0);if(d==null||d.getState()!=Display.STATE_ON)return;
        Point p=new Point();d.getRealSize(p);
        String key=p.x+"x"+p.y+":"+d.getRotation()+":"+inner(d);
        if(!key.equals(request.substring(split+1)))return;
        if(s.screenFade.contentReady(token,SystemClock.uptimeMillis())){
            s.coverToken=0;
            // No visibility change here: the animation draws above a persistent black base.
            Log.i("ProjectionContinuity","COVER_CONTENT_READY backingRetained=true elapsedMs="+(SystemClock.uptimeMillis()-s.coverStarted));
        }
    });}
    static Bundle mirrorLive(boolean renew){
        Bundle result=new Bundle();ProjectionService s=instance;result.putBoolean("service",s!=null);
        if(s!=null)s.main.post(()->{
            if(!s.livePreferred){s.livePreferred=true;s.getSharedPreferences("projection",0).edit().putBoolean("live",true).apply();}
            s.liveUntil=renew?SystemClock.uptimeMillis()+3000:0;
            s.mirrorFold=renew;s.mirrorUntil=s.liveUntil;s.update();
        });
        return result;
    }
    static void mirrorFoldTest(int seconds){ProjectionService s=instance;if(s!=null)s.main.post(()->{
        if(s.mirrorPreview!=null){s.mirrorPreview.close();s.mirrorPreview=null;}
        s.mirrorFold=seconds>0;s.mirrorUntil=seconds<=0?0:SystemClock.uptimeMillis()+Math.min(60,seconds)*1000L;s.update();
    });}
    static Bundle mirrorFrame(){
        Bundle b=new Bundle();ProjectionService s=instance;
        if(s==null)return b;
        Display d=s.displays.getDisplay(0);if(d==null)return b;Point p=new Point();d.getRealSize(p);
        b.putBoolean("alive",s.mirrorPreview!=null&&SystemClock.uptimeMillis()<s.mirrorUntil);
        boolean inner=inner(d);int rotation=d.getRotation();
        b.putBoolean("allowed",allowed);b.putBoolean("inner",inner);b.putInt("rotation",rotation);
        b.putLong("sceneStartedAt",s.entrySceneStarted(p.x,p.y,rotation,inner));
        b.putBoolean("standby",standby);
        b.putLong("coverToken",s.coverToken);b.putLong("coverStartedAt",s.coverStarted);b.putBoolean("coverBacking",s.coverBacking);
        b.putBoolean("screenFadeActive",s.screenFadeActive);b.putFloat("screenDarkness",s.screenDarkness);
        b.putInt("screenFadeMode",s.screenFadeMode);b.putLong("screenFadeAt",s.screenFadeAt);b.putFloat("screenFadeFrom",s.screenFadeFrom);
        Display.Mode mode=d.getMode();
        int expectedWidth=rotation%2==0?mode.getPhysicalWidth():mode.getPhysicalHeight();
        int expectedHeight=rotation%2==0?mode.getPhysicalHeight():mode.getPhysicalWidth();
        b.putBoolean("geometryValid",p.x==expectedWidth&&p.y==expectedHeight);
        b.putBoolean("foldHeld",s.foldHeld);
        b.putFloat("blurStrength",AnimationSettings.blurPercent/100f);
        b.putInt("stretchPercent",AnimationSettings.stretchPercent);
        b.putInt("startAngle",AnimationSettings.startAngle);
        b.putInt("screenWidth",p.x);b.putInt("screenHeight",p.y);b.putInt("state",d.getState());putFoldPose(b);
        return b;
    }
    static void putFoldPose(Bundle b){
        FoldPose pose=foldPose;
        b.putFloat("angle",pose.angle());b.putFloat("rawAngle",pose.rawAngle);
        b.putInt("foldStatus",pose.foldStatus);b.putBoolean("projectionBlocked",pose.blocksProjection());
        b.putInt("contactStatus",pose.contactStatus);
        b.putInt("postureStatus",pose.postureStatus);b.putBoolean("fullyOpened",pose.fullyOpened());
    }
    static void mirrorTest(int seconds){ProjectionService s=instance;if(s!=null)s.main.post(()->{
        s.mirrorFold=false;
        s.mirrorUntil=seconds<=0?0:SystemClock.uptimeMillis()+Math.min(30,seconds)*1000L;s.update();
    });}
    static Bundle mirrorLease(){ProjectionService s=instance;return s!=null&&s.mirrorPreview!=null?s.mirrorPreview.lease():new Bundle();}
    static void mirrorObserve(int seconds){ProjectionService s=instance;if(s!=null)s.main.post(()->{s.mirrorObserveUntil=seconds<=0?0:SystemClock.uptimeMillis()+Math.min(40,seconds)*1000L;s.update();});}
    private final Runnable tick=new Runnable(){public void run(){if(connected){update();main.postDelayed(this,40);}}};
    private final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){update();} public void onDisplayRemoved(int id){update();} public void onDisplayChanged(int id){update();}
    };
    @Override protected void onServiceConnected() {
        AnimationSettings.init(this);
        instance=this;connected=true;
        livePreferred=getSharedPreferences("projection",0).getBoolean("live",false);
        sensors=getSystemService(SensorManager.class);displays=getSystemService(DisplayManager.class);
        ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        if(home!=null && home.activityInfo!=null)homes.add(home.activityInfo.packageName);
        // Match the vendor sensor used by this firmware's physical fold policy.
        // Never infer closure from the active panel: our controller overrides that panel.
        for(Sensor candidate:sensors.getSensorList(Sensor.TYPE_ALL)){
            if(!"xiaomi.sensor.fold_status".equals(candidate.getStringType()))continue;
            if(!"fold_status FOLD_STATUS Wakeup".equals(candidate.getName()))continue;
            physicalFoldSensor=candidate;break;
        }
        // The coarse flag stays CLOSED until about 31 degrees on lhasa.
        // Only this device's contact and posture fields have been checked
        // against real closure, flat tilt, and folding.
        foldPose=new FoldPose(physicalFoldSensor!=null,"lhasa".equals(Build.DEVICE));
        if(physicalFoldSensor!=null&&!sensors.registerListener(this,physicalFoldSensor,20000)){
            physicalFoldSensor=null;foldPose=new FoldPose(false);
        }
        Sensor sensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);if(sensor!=null)sensors.registerListener(this,sensor,20000);
        displays.registerDisplayListener(displayListener,main);status="已开启，等待桌面";main.post(tick);
        Log.i("ProjectionDesktop","CONNECTED homes="+homes+" physicalFold="+(physicalFoldSensor!=null));
        MobileHelper.start(this);

    }
    private boolean home() {
        homeUncertain=false;
        if(getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive())return false;
        for(AccessibilityWindowInfo w:getWindows()) {
            if(w.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION || !w.isActive())continue;
            AccessibilityNodeInfo root=w.getRoot();if(root==null){homeUncertain=true;return false;}
            try {return root.getPackageName()!=null && homes.contains(root.getPackageName().toString());}
            finally {root.recycle();}
        }
        homeUncertain=true;return false;
    }
    private int lockWindowState() {
        // isKeyguardLocked also stays true when a camera/call activity covers it.
        boolean systemUi=false;
        for(AccessibilityWindowInfo w:getWindows()) {
            if(!w.isActive()&&!w.isFocused())continue;
            if(w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)continue;
            AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;
            try {
                String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
                if("com.android.systemui".equals(pkg))systemUi=true;
                else if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION&&!homes.contains(pkg))return -1;
            } finally {root.recycle();}
        }
        return systemUi?1:0;
    }
    private static String key(Display d,Point p) {return d.getMode().getPhysicalWidth()+"x"+d.getMode().getPhysicalHeight()+":"+d.getRotation()+":"+p.x+"x"+p.y;}
    private static boolean inner(Display d) {return Math.min(d.getMode().getPhysicalWidth(),d.getMode().getPhysicalHeight())>=1600;}
    private boolean motion() {return ProjectionMath.endpointOpacity(hinge,primaryInner,AnimationSettings.startAngle,foldPose.blocksProjection())>0;}
    private long[] geometry() {
        long[] value={17,0};AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null)try{if(root.getPackageName()!=null && homes.contains(root.getPackageName().toString()))collect(root,value,0);}finally{root.recycle();}
        return value;
    }
    private void collect(AccessibilityNodeInfo node,long[] value,int depth) {
        if(depth>12 || value[1]>=160 || !node.isVisibleToUser())return;
        Rect b=new Rect();node.getBoundsInScreen(b);
        value[0]=31*(31*(31*(31*value[0]+b.left)+b.top)+b.right)+b.bottom;value[1]++;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo c=node.getChild(i);if(c!=null)try{collect(c,value,depth+1);}finally{c.recycle();}}
    }
    private void update() {
        if(!connected)return;
        hinge=foldPose.angle();
        touchObservation.update(AnimationSettings.swipeRestore);
        long now=SystemClock.uptimeMillis();boolean global=AnimationSettings.globalEnabled;
        boolean home=!global&&home();if(home)lastHomeAt=now;
        Display d=displays.getDisplay(0);primaryInner=d!=null && inner(d);
        if(d!=null){Point size=new Point();d.getRealSize(size);entrySceneStarted(size.x,size.y,d.getRotation(),primaryInner);}
        standby=!getSystemService(PowerManager.class).isInteractive();
        boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked();
        lockScreen=global?locked&&!standby:livePreferred&&lockGate.visible(locked,!standby,locked&&!standby?lockWindowState():0,now);
        allowed=d!=null&&!standby&&(global || home || lockScreen || (homeUncertain && now-lastHomeAt<1000));updatedAt=now;
        holdGate.configure(AnimationSettings.holdSeconds,AnimationSettings.startAngle);
        boolean held=holdGate.update(now,hinge,allowed);
        if(held!=foldHeld){foldHeld=held;Log.i("ProjectionHold",(held?"RETURN_TO_NORMAL":"FOLLOW_HINGE")+" angle="+hinge);}
        long oldCoverToken=coverToken;boolean wasFading=screenFadeActive;
        screenFade.configure(AnimationSettings.openAngle,AnimationSettings.closeAngle);
        screenFade.update(now,hinge,primaryInner,mirrorFold&&now<mirrorUntil&&allowed&&!standby,held);
        coverToken=screenFade.token();coverStarted=screenFade.startedAt();
        coverBacking=screenFade.backing();screenDarkness=screenFade.darkness();screenFadeActive=screenFade.active();
        screenFadeMode=screenFade.mode();screenFadeAt=screenFade.phaseStartedAt();screenFadeFrom=screenFade.cancelFrom();
        if(oldCoverToken!=coverToken)Log.i("ProjectionContinuity","SCREEN_FADE token="+coverToken+" inner="+primaryInner+" elapsedMs="+(now-coverStarted));
        if(wasFading!=screenFadeActive)Log.i("ProjectionContinuity","SCREEN_FADE_ACTIVE "+screenFadeActive+" angle="+hinge+" inner="+primaryInner);
        if(mirrorPreview!=null)mirrorPreview.setBlackout(coverBacking);
        if(mirrorFold&&now<mirrorUntil&&allowed&&d!=null){
            reset();closeWindow();
            if(mirrorPreview==null&&d.getState()==Display.STATE_ON)mirrorPreview=new MirrorPreview(this,d,true);
            Point p=new Point();d.getRealSize(p);String current=key(d,p)+":"+d.getState();
            if(!current.equals(mirrorScene)){fingerSwipe.reset();mirrorScene=current;Log.i("ProjectionContinuity","DISPLAY "+current+" angle="+hinge);}
            status=foldHeld?"悬停使用中，画面已恢复正常":global?"全局开合投影运行中":lockScreen?"连续锁屏投影运行中":"连续桌面投影运行中";
            return;
        }
        if(!mirrorFold&&now<mirrorUntil && allowed && d!=null && d.getState()==Display.STATE_ON){
            Point p=new Point();d.getRealSize(p);String current=key(d,p);
            if(mirrorPreview!=null&&!current.equals(mirrorScene)){Log.i("ProjectionDesktop","MIRROR_STOP geometry "+mirrorScene+" -> "+current);mirrorUntil=0;}
            else{
                reset();closeWindow();
                if(mirrorPreview==null){mirrorScene=current;mirrorPreview=new MirrorPreview(this,d);Log.i("ProjectionDesktop","MIRROR_START "+current);}
                return;
            }
        }
        if(mirrorPreview!=null){Log.i("ProjectionDesktop","MIRROR_STOP remaining="+(mirrorUntil-now)+" home="+home+" allowed="+allowed+" display="+(d==null?-1:d.getState()));mirrorPreview.close();mirrorPreview=null;mirrorUntil=0;}
        if(now>=mirrorUntil)mirrorFold=false;
        if(livePreferred){reset();closeWindow();status=now<liveUntil?(global?"连续投影已就绪，等待亮屏":"连续投影已就绪，等待桌面或锁屏"):"连续投影助手未连接";return;}
        if(now<mirrorObserveUntil){reset();closeWindow();return;}
        if(d==null || d.getState()!=Display.STATE_ON || !home) {reset();closeWindow();scene="";return;}
        Point size=new Point();d.getRealSize(size);
        Display.Mode mode=d.getMode();int r=d.getRotation();
        int ew=r%2==0?mode.getPhysicalWidth():mode.getPhysicalHeight(),eh=r%2==0?mode.getPhysicalHeight():mode.getPhysicalWidth();
        if(size.x!=ew || size.y!=eh) {reset();closeWindow();scene="";return;}
        String next=key(d,size);
        if(!next.equals(scene)){reset();closeWindow();scene=next;attempts=0;}
        ensureWindow(d);
        if(window==null)return;
        if(!motion()){
            if(overlay!=null&&primaryInner&&foldPose.fullyOpened()&&overlay.finishFlat(now))return;
            if(overlay!=null || pending)reset();attempts=0;status="桌面投影已就绪";return;
        }
        long[] shape=geometry();
        if(overlay!=null){overlay.follow(hinge);if(shape[1]>=8 && shape[0]!=shownSignature){reset();attempts=0;}else return;}
        if(!gate.observe(scene,shape[0],(int)shape[1],now) || pending || now<retryAt || attempts>=3)return;
        capture(d,size,shape[0]);
    }
    private void capture(Display d,Point size,long signature) {
        pending=true;attempts++;int request=++generation;String captured=scene;int rotation=d.getRotation();boolean inner=primaryInner;
        long started=SystemClock.uptimeMillis();retryAt=started+350;status="正在准备桌面投影";
        takeScreenshot(0,getMainExecutor(),new TakeScreenshotCallback(){
            public void onSuccess(ScreenshotResult result) {
                Bitmap screenshot=null;
                try {
                    if(request!=generation || !connected)return;
                    screenshot=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                    if(screenshot==null || screenshot.getWidth()!=size.x || screenshot.getHeight()!=size.y || geometry()[0]!=signature){pending=false;return;}
                    Bitmap owned=screenshot;screenshot=null;long captureMs=SystemClock.uptimeMillis()-started;
                    worker.execute(()->{
                        GpuLayers layers=null;
                        try {
                            layers=GpuLayers.bake(owned,inner,rotation);
                            GpuLayers ready=layers;layers=null;
                            main.post(()->finish(request,captured,size,signature,inner,rotation,started,captureMs,ready));
                        } catch(Exception e){Log.e("ProjectionDesktop","GPU bake failed",e);main.post(()->{if(request==generation){pending=false;status="准备失败，本次保留原桌面";}});}
                        finally{owned.recycle();if(layers!=null)layers.close();}
                    });
                } catch(RuntimeException e){pending=false;Log.e("ProjectionDesktop","capture callback",e);}
                finally{result.getHardwareBuffer().close();if(screenshot!=null)screenshot.recycle();}
            }
            public void onFailure(int error){if(request!=generation)return;pending=false;status="截图暂不可用";Log.w("ProjectionDesktop","CAPTURE_ERROR "+error);}
        });
    }
    private void finish(int request,String captured,Point size,long signature,boolean inner,int rotation,long started,long captureMs,GpuLayers layers) {
        if(request==generation)pending=false;
        Display d=displays.getDisplay(0);Point current=new Point();if(d!=null)d.getRealSize(current);
        boolean valid=d!=null && FrameGate.accepts(generation,request,scene,captured,size.x,size.y,current.x,current.y,
            connected && home() && motion(),d.getState()==Display.STATE_ON,SystemClock.uptimeMillis()-started) && key(d,current).equals(captured);
        if(!valid || geometry()[0]!=signature){layers.close();Log.i("ProjectionDesktop","DISCARD stale/geometry ageMs="+(SystemClock.uptimeMillis()-started));return;}
        DesktopProjection view=null;
        try {
            if(window==null){layers.close();return;}
            view=new DesktopProjection(window.getContext(),layers,inner,rotation,hinge);
            window.addView(view,new FrameLayout.LayoutParams(-1,-1));overlay=view;shownSignature=signature;status="桌面投影运行中";
            Log.i("ProjectionDesktop","SHOWN inner="+inner+" angle="+hinge+" captureMs="+captureMs+" totalMs="+(SystemClock.uptimeMillis()-started));
        }catch(Exception e){if(view!=null)view.release();else layers.close();Log.e("ProjectionDesktop","show failed",e);}
    }
    private void reset() {
        if(pending || overlay!=null)generation++;pending=false;
        if(overlay!=null){DesktopProjection old=overlay;overlay=null;if(window!=null)window.removeView(old);old.release();}
    }
    private void ensureWindow(Display display) {
        if(window!=null)return;
        Context context=createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null);
        manager=context.getSystemService(WindowManager.class);
        FrameLayout candidate=new FrameLayout(context);candidate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.gravity=Gravity.TOP|Gravity.LEFT;p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        p.preferredRefreshRate=120;p.setTitle("ReferencePlane desktop projection");
        try{manager.addView(candidate,p);window=candidate;}catch(RuntimeException e){Log.e("ProjectionDesktop","warm window",e);}
    }
    private void closeWindow() {
        if(window==null)return;FrameLayout old=window;window=null;
        try{manager.removeViewImmediate(old);}catch(RuntimeException e){Log.w("ProjectionDesktop","window remove",e);}
    }
    static void stop() {ProjectionService service=instance;if(service!=null)service.main.post(service::disableSelf);}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){update();}
    @Override public void onMotionEvent(MotionEvent e){
        long now=SystemClock.uptimeMillis();
        if(!AnimationSettings.swipeRestore||!touchObservation.ready()||!allowed||!e.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
                ||e.getPointerCount()!=1||now-e.getEventTime()<0||now-e.getEventTime()>500){fingerSwipe.reset();return;}
        int action=e.getActionMasked(),id=e.getPointerId(0),screen=TouchObservation.displayId(e);
        if(screen!=Display.DEFAULT_DISPLAY){fingerSwipe.reset();return;}
        if(action==MotionEvent.ACTION_DOWN){fingerSwipe.down(id,screen,e.getX(),e.getY(),e.getEventTime());return;}
        if(action!=MotionEvent.ACTION_MOVE){fingerSwipe.reset();return;}
        float threshold=Math.max(ViewConfiguration.get(this).getScaledTouchSlop()*2,20*getResources().getDisplayMetrics().density);
        boolean swiped=false;
        for(int i=0;i<e.getHistorySize()&&!swiped;i++)swiped=fingerSwipe.move(id,screen,e.getHistoricalX(0,i),e.getHistoricalY(0,i),e.getHistoricalEventTime(i),threshold);
        if(!swiped)swiped=fingerSwipe.move(id,screen,e.getX(),e.getY(),e.getEventTime(),threshold);
        if(swiped){
            Log.i("ProjectionTouch","FINGER_SWIPE");
            if(holdGate.restore(now,hinge,connected&&allowed&&mirrorFold&&now<mirrorUntil)){
                Log.i("ProjectionHold","FINGER_RESTORE angle="+hinge);update();
            }
        }
    }
    @Override public void onInterrupt(){reset();}
    public void onSensorChanged(SensorEvent e){
        if(e.values.length==0)return;
        FoldPose before=foldPose,next=before;
        if(e.sensor==physicalFoldSensor)next=before.withFoldEvent(e.values);
        else if(e.sensor.getType()==Sensor.TYPE_HINGE_ANGLE)next=before.withAngle(e.values[0]);
        if(next==before)return;
        foldPose=next;
        if(next.foldStatus!=before.foldStatus||next.contactStatus!=before.contactStatus||next.postureStatus!=before.postureStatus||next.fullyOpened()!=before.fullyOpened()){
            fingerSwipe.reset();
            Log.i("ProjectionFold","PHYSICAL status="+next.foldStatus+" contact="+next.contactStatus+" posture="+next.postureStatus+" rawAngle="+next.rawAngle+" blocked="+next.blocksProjection());
        }
        update();
    }
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onDestroy(){touchObservation.close();connected=false;instance=null;allowed=false;updatedAt=0;status="已停用";MobileHelper.stop();main.removeCallbacks(tick);if(mirrorPreview!=null){mirrorPreview.close();mirrorPreview=null;}reset();closeWindow();sensors.unregisterListener(this);displays.unregisterDisplayListener(displayListener);worker.shutdown();super.onDestroy();}
}
