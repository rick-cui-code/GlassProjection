package io.github.sixzleo.tabfold.projection;

import android.accessibilityservice.*;
import android.os.*;
import android.util.Log;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/** Own service connection only; system permissions remain enforced in the Shizuku host. */
final class TouchObservation {
    private final AccessibilityService service;
    private boolean pending,enabled,closed;
    private long retryAt;
    private java.lang.reflect.Field localSources;
    TouchObservation(AccessibilityService service){this.service=service;}
    boolean ready(){return enabled;}
    static int displayId(android.view.MotionEvent event){
        try{return (Integer)HiddenApiBypass.invoke(android.view.InputEvent.class,event,"getDisplayId");}
        catch(Exception|LinkageError e){return -1;}
    }
    void update(boolean wanted){
        if(closed)return;
        if(!wanted||!MobileHelper.available()){if(enabled){clear();enabled=false;}return;}
        if(enabled||pending||SystemClock.uptimeMillis()<retryAt)return;
        pending=true;retryAt=SystemClock.uptimeMillis()+10000;
        try{
            if(localSources==null){
                for(java.lang.reflect.Field f:HiddenApiBypass.getInstanceFields(AccessibilityService.class))
                    if(f.getName().equals("mMotionEventSources")){localSources=f;f.setAccessible(true);break;}
                if(localSources==null)throw new NoSuchFieldException("Own service motion callback mask");
            }
            int id=(Integer)HiddenApiBypass.invoke(AccessibilityService.class,service,"getConnectionId");
            Object c=HiddenApiBypass.invoke(Class.forName("android.view.accessibility.AccessibilityInteractionClient"),null,"getConnection",id);
            IBinder binder=((IInterface)c).asBinder();
            MobileHelper.observeTouch(binder,okay->{
                pending=false;enabled=okay;
                // The privileged setter updates the server. Synchronize this instance's
                // callback filter without resubmitting the privileged info as app UID.
                try{localSources.setInt(service,okay?android.view.InputDevice.SOURCE_TOUCHSCREEN:0);}
                catch(IllegalAccessException e){clear();enabled=false;}
                if(closed||!AnimationSettings.swipeRestore){clear();enabled=false;}
            });
        }catch(Exception|LinkageError e){pending=false;Log.w("ProjectionTouch","Own service connection unavailable",e);}
    }
    private void clear(){
        if(Build.VERSION.SDK_INT>=34)try{AccessibilityServiceInfo info=service.getServiceInfo();if(info!=null){info.setMotionEventSources(0);service.setServiceInfo(info);}}catch(RuntimeException e){Log.w("ProjectionTouch","Observer cleanup",e);}
    }
    void resume(){closed=false;retryAt=0;}
    void close(){closed=true;clear();enabled=false;}
}
