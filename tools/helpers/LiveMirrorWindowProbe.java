package io.github.sixzleo.tabfold.probe;

import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.opengl.*;
import android.os.*;
import android.view.*;
import java.nio.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ten-second local GPU mirror preview. Own window only, excluded from capture. */
public final class LiveMirrorWindowProbe {
    static Object am,provider;
    static IBinder providerToken;
    static java.lang.reflect.Method providerCall;
    static final String AUTHORITY="io.github.sixzleo.tabfold.projection.surface";
    static Surface leasedOutput;
    static SurfaceControl leasedControl;
    static int leasedCanvasSize;
    static Handler main;
    static volatile boolean stopped;
    static volatile boolean failed;
    static final AtomicBoolean finishing=new AtomicBoolean();
    static boolean foldMode;
    static boolean continuous;
    static volatile long progressAt;
    static long renewAt;
    static java.nio.channels.FileLock processLock;
    static java.io.RandomAccessFile lockFile;
    static int durationMs=10000;
    static final String VERT="attribute vec2 pos; varying vec2 uv; void main(){gl_Position=vec4(pos,0.,1.);uv=vec2((pos.x+1.)*.5,(1.-pos.y)*.5);}";
    static final String FRAG="#extension GL_OES_EGL_image_external : require\nprecision highp float; uniform samplerExternalOES source; uniform mat4 tex; uniform float tilt; varying vec2 uv;\n"
        +"vec3 sampleAt(vec2 p){if(p.x<0.||p.y<0.||p.x>1.||p.y>1.)return vec3(0.);return texture2D(source,(tex*vec4(p.x,1.-p.y,0.,1.)).xy).rgb;}\n"
        +"void main(){float x=uv.x*.073;float y=(uv.y-.5)*.16;vec3 P=vec3(x*cos(tilt),y,x*sin(tilt));vec3 D=normalize(P-vec3(.0365,0.,.6));float t=-P.z/min(D.z,-.0001);vec3 Q=P+t*D;vec2 q=vec2(Q.x/.073,Q.y/.16+.5);"
        +"float s=min(.008,max(t,0.)*.35);vec3 c=vec3(0.);float wsum=0.;for(int iy=-2;iy<=2;iy++){for(int ix=-2;ix<=2;ix++){float w=exp(-.5*float(ix*ix+iy*iy));c+=pow(sampleAt(q+vec2(float(ix),float(iy)*.4264)*s),vec3(2.2))*w;wsum+=w;}}"
        +"c=pow(c/wsum,vec3(1./2.2));if(uv.x<.006||uv.x>.994||uv.y<.004||uv.y>.996)c=vec3(.1,.9,.8);gl_FragColor=vec4(c,1.);}";
    static final String FOLD_FRAG="precision highp float;uniform sampler2D level0,level1,level2,level3,level4,level5,level6;uniform float bufferSize,contentScale,canvasSize;uniform float tilt,inner,turn,opacity,blurStrength;uniform vec2 screen;varying vec2 uv;"
        +"vec2 canonical(vec2 p){if(turn<.5)return p;if(turn<1.5)return vec2(1.-p.y,p.x);if(turn<2.5)return vec2(1.-p.x,1.-p.y);return vec2(p.y,1.-p.x);}"
        +"vec2 toScreen(vec2 p){if(turn<.5)return p;if(turn<1.5)return vec2(p.y,1.-p.x);if(turn<2.5)return vec2(1.-p.x,1.-p.y);return vec2(1.-p.y,p.x);}"
        +"vec3 layer(vec2 p,float l){vec3 c;if(l<.5)c=texture2D(level0,p).rgb;else if(l<1.5)c=texture2D(level1,p).rgb;else if(l<2.5)c=texture2D(level2,p).rgb;else if(l<3.5)c=texture2D(level3,p).rgb;else if(l<4.5)c=texture2D(level4,p).rgb;else if(l<5.5)c=texture2D(level5,p).rgb;else c=texture2D(level6,p).rgb;return pow(c,vec3(2.2));}"
        +"vec3 at(vec2 p,float sigma){p=toScreen(p);vec2 extent=screen/max(screen.x,screen.y);p=(1.-extent)*.5+p*extent;p=(1.-contentScale)*.5+p*contentScale;p.y=1.-p.y;float variance=sigma*sigma;float l=clamp(.5*log2(1.+3.*variance/2.854),0.,6.);float lo=floor(l);float a=2.854*(pow(4.,lo)-1.)/3.;float b=2.854*(pow(4.,min(lo+1.,6.))-1.)/3.;float f=clamp((variance-a)/max(b-a,.0001),0.,1.);return pow(mix(layer(p,lo),layer(p,min(lo+1.,6.)),f),vec3(1./2.2));}"
        +"void main(){vec2 p=canvasSize>0.?uv*canvasSize/screen:uv;if(p.x>1.||p.y>1.){gl_FragColor=vec4(0.);return;}vec2 u=canonical(p);if(opacity<.001||(inner>.5&&u.x>=.5)){gl_FragColor=vec4(0.);return;}float W=inner>.5?.146:.073;float x=(u.x-(inner>.5?.5:0.))*W;float y=(u.y-.5)*.16;vec3 P=vec3(x*cos(tilt),y,abs(x)*sin(tilt));vec3 D=normalize(P-vec3(inner>.5?0.:.0365,0.,.6));float t=-P.z/min(D.z,-.0001);vec3 Q=P+t*D;vec2 q=vec2(Q.x/W+(inner>.5?.5:0.),Q.y/.16+.5);"
        +"float s=min(.012,max(t,0.)*(inner>.5?.25:.35));vec2 canonicalSize=mod(turn,2.)<.5?screen:screen.yx;float sigma=s*canonicalSize.x/max(screen.x,screen.y)*bufferSize*contentScale*blurStrength;"
        +"vec3 c=at(q,sigma);"
        // Keep interior blur intact; tighten only the inner paper/black boundary.
        +"if(inner>.5&&sigma>.1){vec2 edge=min(q,vec2(1.)-q)*canonicalSize/max(screen.x,screen.y)*bufferSize*contentScale;float distance=abs(min(edge.x,edge.y));float keep=smoothstep(sigma,3.*sigma,distance);if(keep<.999)c=mix(at(q,sigma*.65),c,keep);}"
        +"gl_FragColor=vec4(c*opacity,opacity);}";
    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());
            continuous=args.length>0&&"live".equals(args[0]);
            foldMode=continuous||(args.length>0&&"fold".equals(args[0]));
            if(continuous){
                lockFile=new java.io.RandomAccessFile("/data/local/tmp/tabfold-live.lock","rw");
                processLock=lockFile.getChannel().tryLock();
                if(processLock==null){System.out.println("Already running");System.exit(0);return;}
            }else if(foldMode)durationMs=args.length>1?Math.max(5,Math.min(55,Integer.parseInt(args[1])))*1000:50000;
            Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
            am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);providerToken=new Binder();
            Object holder=Class.forName("android.app.IActivityManager").getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(am,AUTHORITY,0,providerToken,"LiveMirrorPreview");
            if(holder==null)throw new IllegalStateException("No projection provider");
            provider=holder.getClass().getField("provider").get(holder);
            providerCall=Class.forName("android.content.IContentProvider").getMethod("call",android.content.AttributionSource.class,String.class,String.class,String.class,Bundle.class);
            if(continuous){
                progressAt=SystemClock.uptimeMillis();
                new Thread(LiveMirrorWindowProbe::runContinuous,"mirror-supervisor").start();
                main.postDelayed(new Runnable(){public void run(){
                    if(new java.io.File("/data/local/tmp/tabfold-live.stop").exists()||SystemClock.uptimeMillis()-progressAt>5000){stopped=true;finish();}
                    else main.postDelayed(this,500);
                }},500);
                Looper.loop();return;
            }
            call(foldMode?"mirror-fold":"mirror-preview",foldMode?"60":"15");
            Bundle lease=null;
            long deadline=SystemClock.uptimeMillis()+2000;
            while(SystemClock.uptimeMillis()<deadline){lease=call("mirror-lease",null);leasedOutput=lease.getParcelable("surface");if(leasedOutput!=null)break;Thread.sleep(20);}
            if(leasedOutput==null)throw new IllegalStateException("Desktop service/preview unavailable");
            leasedControl=lease.getParcelable("control");
            leasedCanvasSize=lease.getInt("canvasSize");
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,leasedControl,true);t.apply();
            }
            final int width=lease.getInt("width"),height=lease.getInt("height");
            new Thread(()->render(leasedOutput,width,height),"mirror-gpu").start();
            main.postDelayed(()->{stopped=true;finish();},durationMs+3000);
            Looper.loop();
        }catch(Throwable error){failed=true;error.printStackTrace();finish();}
    }
    static Bundle call(String method,String arg)throws Exception {
        return (Bundle)providerCall.invoke(provider,new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build(),AUTHORITY,method,arg,null);
    }
    static void renew()throws Exception{
        long now=SystemClock.uptimeMillis();progressAt=now;
        if(now>=renewAt){call("mirror-live","1");renewAt=now+500;}
    }
    static void releaseOutput(){
        if(leasedControl!=null){leasedControl.release();leasedControl=null;}
        if(leasedOutput!=null){leasedOutput.release();leasedOutput=null;}
    }
    static void runContinuous(){
        try{
            while(!stopped){
                renew();Bundle lease=call("mirror-lease",null);
                leasedOutput=lease.getParcelable("surface");
                if(leasedOutput==null){Thread.sleep(100);continue;}
                leasedControl=lease.getParcelable("control");
                leasedCanvasSize=lease.getInt("canvasSize");
                try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                    SurfaceControl.Transaction.class.getMethod("setSkipScreenshot",SurfaceControl.class,boolean.class).invoke(t,leasedControl,true);t.apply();
                }
                render(leasedOutput,lease.getInt("width"),lease.getInt("height"));
                releaseOutput();
                if(failed)break;
                Thread.sleep(100);
            }
        }catch(Throwable error){failed=true;error.printStackTrace();}
        finally{finish();}
    }
    static void finish(){
        stopped=true;
        if(!finishing.compareAndSet(false,true))return;
        if(main==null){System.exit(1);return;}
        try{
            if(providerCall!=null)call(continuous?"mirror-live":foldMode?"mirror-fold":"mirror-preview","0");
            if(providerToken!=null)Class.forName("android.app.IActivityManager").getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(am,AUTHORITY,providerToken,0);
        }catch(Throwable e){e.printStackTrace();}
        if(leasedControl!=null)leasedControl.release();if(leasedOutput!=null)leasedOutput.release();
        System.out.println("CLEANED preview window");System.exit(failed?1:0);
    }
    static int shader(int type,String code){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
    static void render(Surface output,int width,int height){
        EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLContext context=EGL14.EGL_NO_CONTEXT;EGLSurface window=EGL14.EGL_NO_SURFACE;
        VirtualDisplay mirror=null;Surface input=null;SurfaceTexture texture=null;
        LiveBlurPyramid pyramid=null;
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];
            if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("eglInitialize");
            EGLConfig[] configs=new EGLConfig[1];int[] n=new int[1];
            EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,n,0);
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            window=EGL14.eglCreateWindowSurface(display,configs[0],output,new int[]{EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,window,window,context))throw new IllegalStateException("eglMakeCurrent");
            int program=GLES20.glCreateProgram();GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,VERT));GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,foldMode?FOLD_FRAG:FRAG));GLES20.glLinkProgram(program);
            int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            GLES20.glUseProgram(program);int[] textures=new int[1];GLES20.glGenTextures(1,textures,0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textures[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            texture=new SurfaceTexture(textures[0]);texture.setDefaultBufferSize(width,height);AtomicBoolean ready=new AtomicBoolean();texture.setOnFrameAvailableListener(t->ready.set(true),main);input=new Surface(texture);
            long start=SystemClock.uptimeMillis();
            mirror=(VirtualDisplay)DisplayManager.class.getMethod("createVirtualDisplay",String.class,int.class,int.class,int.class,Surface.class).invoke(null,"TabFold-live-gpu-preview",width,height,0,input);
            FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
            if(foldMode)pyramid=new LiveBlurPyramid(width,vertices);
            GLES20.glUseProgram(program);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"canvasSize"),leasedCanvasSize);
            int pos=GLES20.glGetAttribLocation(program,"pos");GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"source"),0);GLES20.glViewport(0,0,width,height);
            int frames=0,sourceFrames=0;float[] matrix=new float[16];boolean saved=false,hasTexture=false;
            long pollAt=0,lastDraw=0;Bundle geometry=null;String previousGeometry="";float eased=Float.NaN;
            FoldReturnMotion returnMotion=new FoldReturnMotion();
            boolean wasHeld=false,returnComplete=false;
            while(!stopped&&(continuous||SystemClock.uptimeMillis()-start<durationMs)){
                long now=SystemClock.uptimeMillis();
                if(continuous)renew();
                if(ready.getAndSet(false)){
                    if(foldMode)GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
                    texture.updateTexImage();texture.getTransformMatrix(matrix);hasTexture=true;sourceFrames++;
                    if(pyramid!=null){pyramid.update(textures[0],matrix);pyramid.bind(program,width,height);}
                }
                if(!hasTexture){Thread.sleep(2);continue;}
                if(foldMode){
                    if(now>=pollAt){geometry=call("mirror-frame",null);pollAt=now+12;}
                    if(geometry==null||!geometry.getBoolean("alive")||!geometry.getBoolean("allowed"))break;
                    boolean inner=geometry.getBoolean("inner");int rotation=geometry.getInt("rotation"),sw=geometry.getInt("screenWidth"),sh=geometry.getInt("screenHeight");
                    float angle=geometry.getFloat("angle",Float.NaN);if(!Float.isFinite(angle))break;
                    String key=sw+"x"+sh+":"+rotation+":"+inner+":"+geometry.getInt("state");
                    if(!key.equals(previousGeometry)){System.out.println("OUTPUT_GEOMETRY elapsedMs="+(now-start)+" "+key+" angle="+angle+" frames="+frames);previousGeometry=key;eased=angle;}
                    float step=lastDraw==0?1:(float)(1-Math.exp(-(now-lastDraw)/28.0));if(!Float.isFinite(eased))eased=angle;eased+=(angle-eased)*step;
                    float tilt=Math.min(85,Math.max(0,inner?180-eased:eased));
                    float opacity=Math.min(1,Math.max(0,inner?(175-angle)/10:(angle-3)/5));
                    boolean held=geometry.getBoolean("foldHeld");
                    if(held!=wasHeld){System.out.println("HOLD_"+(held?"RETURN_START":"RESUME")+" angle="+angle);wasHeld=held;returnComplete=false;}
                    float amount=returnMotion.update(now,held);
                    if(held&&amount==0&&!returnComplete){System.out.println("HOLD_RETURN_COMPLETE angle="+angle);returnComplete=true;}
                    // Reverse the projection itself; fade only its final, nearly flat frames.
                    tilt*=amount;
                    opacity*=FoldReturnMotion.coverage(amount);
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"tilt"),(float)Math.toRadians(tilt));
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"inner"),inner?1:0);
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"turn"),inner?(rotation+1)%4:rotation);
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"opacity"),opacity);
                    float strength=geometry.getFloat("blurStrength",1f);
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"blurStrength"),Float.isFinite(strength)?Math.max(0,Math.min(2,strength)):1f);
                    GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"screen"),sw,sh);
                }else{
                    float angle=(float)(.5-.5*Math.cos((now-start)/10000.0*Math.PI*2))*.65f;
                    GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"tilt"),angle);
                }
                lastDraw=now;
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"tex"),1,false,matrix,0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                if(continuous){java.io.File request=new java.io.File("/data/local/tmp/tabfold-live.capture");if(request.exists()&&request.delete()){
                    saveFrame(width,height);System.out.println("CAPTURED_GPU_FRAME "+previousGeometry+" angle="+eased);
                }}
                if(!foldMode&&!saved&&SystemClock.uptimeMillis()-start>3000){saveFrame(width,height);saved=true;}
                if(!EGL14.eglSwapBuffers(display,window))throw new IllegalStateException("swap buffers");
                if(frames++==0)System.out.println("FIRST_PREVIEW_FRAME ms="+(SystemClock.uptimeMillis()-start));
            }
            System.out.println("PREVIEW frames="+frames+" sourceFrames="+sourceFrames+" elapsedMs="+(SystemClock.uptimeMillis()-start));
        }catch(Throwable error){failed=true;error.printStackTrace();}
        finally {
            if(mirror!=null)mirror.release();if(input!=null)input.release();if(texture!=null)texture.release();
            if(pyramid!=null)pyramid.close();
            if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}
            if(!continuous)finish();
        }
    }
    static void saveFrame(int w,int h)throws Exception {
        ByteBuffer bytes=ByteBuffer.allocateDirect(w*h*4);GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,bytes);
        int[] pixels=new int[w*h];int transparent=0,feathered=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=((h-1-y)*w+x)*4,a=bytes.get(p+3)&255;
            if(a==0)transparent++;else if(a<255)feathered++;
            int r=a==0?0:Math.min(255,(bytes.get(p)&255)*255/a);
            int g=a==0?0:Math.min(255,(bytes.get(p+1)&255)*255/a);
            int b=a==0?0:Math.min(255,(bytes.get(p+2)&255)*255/a);
            pixels[y*w+x]=(a<<24)|(r<<16)|(g<<8)|b;
        }
        System.out.println("CAPTURE_ALPHA transparent="+transparent+" feathered="+feathered);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(pixels,w,h,android.graphics.Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream("/data/local/tmp/tabfold-live-projection.png")){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
