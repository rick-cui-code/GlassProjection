package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Binder;
import android.accessibilityservice.AccessibilityService;
import android.view.*;

/** Touch-transparent output; display attachment avoids window-token handoff animations. */
final class MirrorPreview implements SurfaceHolder.Callback {
    private WindowManager manager;
    private SurfaceControlViewHost host;
    private SurfaceControlViewHost.SurfacePackage hostPackage;
    private SurfaceControl displayControl,blackoutControl;
    private android.graphics.Bitmap blackoutBitmap;
    private final long blackoutId=android.os.SystemClock.uptimeMillis();
    private boolean blackoutReady,blackoutVisible;
    private final Binder ownerToken=new Binder();
    private final SurfaceView view;
    private final int width,height,rotation;
    private volatile boolean ready;
    private int created;
    private final boolean displayAttached;
    MirrorPreview(Context service,Display display){this(service,display,false);}
    MirrorPreview(Context service,Display display,boolean persistent){
        Point size=new Point();display.getRealSize(size);
        width=persistent?1182:size.x/2;height=persistent?1182:size.y/2;rotation=display.getRotation();
        displayAttached=persistent&&android.os.Build.VERSION.SDK_INT>=34&&service instanceof AccessibilityService;
        Context context=service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,null);
        view=new SurfaceView(context){
            @Override public void setLayoutParams(ViewGroup.LayoutParams params){
                // The public SCVH setView overload creates its own window params.
                if(params instanceof WindowManager.LayoutParams){
                    WindowManager.LayoutParams w=(WindowManager.LayoutParams)params;
                    w.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    w.setTitle("Glass display output");
                }
                super.setLayoutParams(params);
            }
        };view.setZOrderOnTop(true);
        // Present in the original 23:34 APK: do not recreate the surface on panel visibility changes.
        if(persistent&&android.os.Build.VERSION.SDK_INT>=34)
            view.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.getHolder().setFormat(PixelFormat.TRANSLUCENT);view.getHolder().addCallback(this);
        if(persistent)view.getHolder().setFixedSize(width,height);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(persistent?-1:width,persistent?-1:height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=persistent?0:20;p.y=persistent?0:120;
        p.preferredRefreshRate=120;p.setTitle("Bounded live mirror preview");
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        if(displayAttached&&android.os.Build.VERSION.SDK_INT>=34){
            try {
                host=new SurfaceControlViewHost(context,display,new Binder());
                host.setView(view,width,height);
                hostPackage=host.getSurfacePackage();
                if(hostPackage==null)throw new IllegalStateException("Missing display output package");
                displayControl=hostPackage.getSurfaceControl();
                // Fixed canvas: shader clips to current logical display, with no window relayout.
                try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                    t.setScale(displayControl,2f,2f).setPosition(displayControl,0,0)
                        .setLayer(displayControl,1).setAlpha(displayControl,1f).setVisibility(displayControl,true).apply();
                }
                android.graphics.Bitmap pixel=android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888);
                pixel.eraseColor(android.graphics.Color.BLACK);
                blackoutBitmap=pixel.copy(android.graphics.Bitmap.Config.HARDWARE,false);pixel.recycle();
                if(blackoutBitmap==null)throw new IllegalStateException("Blackout buffer unavailable");
                blackoutControl=new SurfaceControl.Builder().setName("Glass cover handoff black")
                    .setParent(displayControl).setBufferSize(1,1).setOpaque(true).setHidden(true).build();
                try(android.hardware.HardwareBuffer buffer=blackoutBitmap.getHardwareBuffer();SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                    t.setBuffer(blackoutControl,buffer).setScale(blackoutControl,width,height)
                        .setPosition(blackoutControl,0,0).setLayer(blackoutControl,-1).apply();
                }
                ((AccessibilityService)service).attachAccessibilityOverlayToDisplay(display.getDisplayId(),displayControl);
                android.util.Log.i("ProjectionContinuity","DISPLAY_ATTACHED canvas="+(width*2)+"x"+(height*2));
            }catch(RuntimeException e){close();throw e;}
        }else{
            manager=context.getSystemService(WindowManager.class);manager.addView(view,p);
        }
    }
    Bundle lease(){Bundle b=new Bundle();if(ready&&view.getHolder().getSurface().isValid()){
        b.putParcelable("surface",view.getHolder().getSurface());b.putParcelable("control",view.getSurfaceControl());
        if(displayControl!=null){b.putParcelable("rootControl",displayControl);b.putBinder("ownerToken",ownerToken);}
        b.putInt("width",width);b.putInt("height",height);b.putInt("rotation",rotation);
        b.putInt("surfaceGeneration",created);
        if(blackoutControl!=null){b.putParcelable("blackoutControl",blackoutControl);b.putLong("blackoutId",blackoutId);}
        b.putInt("canvasSize",displayAttached?width*2:0);
    }return b;}
    void prepareBlackout(long id){
        if(id!=blackoutId||blackoutControl==null)return;
        // Helper sets the relative layer with its authorized shell API access.
        blackoutReady=true;
    }
    void setBlackout(boolean visible){
        visible=visible&&blackoutReady;
        if(blackoutControl==null||!blackoutControl.isValid()||visible==blackoutVisible)return;
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(blackoutControl,visible).apply();}
        blackoutVisible=visible;
        android.util.Log.i("ProjectionContinuity","COVER_BLACK "+visible);
    }
    void close(){
        ready=false;
        if(displayControl!=null&&displayControl.isValid())try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
            t.setVisibility(displayControl,false).reparent(displayControl,null).apply();
        }
        if(blackoutControl!=null){blackoutControl.release();blackoutControl=null;}
        if(blackoutBitmap!=null){blackoutBitmap.recycle();blackoutBitmap=null;}
        displayControl=null;
        if(host!=null){host.release();host=null;}
        if(hostPackage!=null){hostPackage.release();hostPackage=null;}
        if(manager!=null&&view.isAttachedToWindow())manager.removeViewImmediate(view);
    }
    public void surfaceCreated(SurfaceHolder h){ready=true;created++;h.getSurface().setFrameRate(120,Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);android.util.Log.i("ProjectionContinuity","SURFACE_CREATED generation="+created);}
    public void surfaceChanged(SurfaceHolder h,int f,int w,int he){ready=true;android.util.Log.i("ProjectionContinuity","SURFACE_CHANGED buffer="+w+"x"+he+" view="+view.getWidth()+"x"+view.getHeight());}
    public void surfaceDestroyed(SurfaceHolder h){ready=false;android.util.Log.i("ProjectionContinuity","SURFACE_DESTROYED generation="+created);}
}
