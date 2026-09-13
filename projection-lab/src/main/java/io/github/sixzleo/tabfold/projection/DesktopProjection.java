package io.github.sixzleo.tabfold.projection;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class DesktopProjection extends View {
    private final GpuLayers layers;
    private final RuntimeShader shader;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    final boolean inner;
    final int rotation;
    float angle;
    private float smoothed;
    private final ProjectionEntrance entry=new ProjectionEntrance();
    private final ProjectionAngleMotion angleMotion=new ProjectionAngleMotion();
    private long flatRequestedAt=-1;
    private long started;
    private boolean closed;
    DesktopProjection(Context context,GpuLayers layers,boolean inner,int rotation,float angle) throws IOException {
        super(context);this.layers=layers;this.inner=inner;this.rotation=rotation;this.angle=angle;smoothed=angle;
        try(InputStream input=getResources().openRawResource(R.raw.reference_plane)) {
            shader=new RuntimeShader(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        for(int i=0;i<8;i++) {
            BitmapShader texture=new BitmapShader(layers.bitmaps[i],Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            Matrix tile=new Matrix();tile.setTranslate(-(i%2)*(layers.width+2*GpuLayers.PAD),-(i/2)*(layers.height+2*GpuLayers.PAD));
            texture.setLocalMatrix(tile);
            texture.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);shader.setInputShader("layer"+i,texture);
        }
        shader.setFloatUniform("size",layers.width,layers.height);
        shader.setFloatUniform("spillFraction",ProjectionMath.INNER_SPILL_FRACTION);
        shader.setFloatUniform("hingeDistanceFraction",ProjectionMath.OUTER_HINGE_DISTANCE_FRACTION);
        shader.setFloatUniform("liveBackdrop",1);
        shader.setFloatUniform("inner",inner?1:0);shader.setFloatUniform("enabled",1);shader.setFloatUniform("diagnostic",0);
        paint.setShader(shader);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    @Override protected void onDraw(Canvas canvas) {
        if(closed)return;
        long now=SystemClock.uptimeMillis();
        if(started==0) {started=now;android.util.Log.i("ProjectionDesktop","FIRST_DRAW inner="+inner+" angle="+angle);}
        FoldPose pose=ProjectionService.foldPose;
        boolean blocked=ProjectionAngleMotion.hardBlocked(pose.blocksProjection(),pose.fullyOpened(),inner);
        smoothed=angleMotion.update(now,angle,inner,pose.blocksProjection(),pose.fullyOpened(),false);
        boolean visible=ProjectionMath.endpointOpacity(inner?smoothed:angle,inner,AnimationSettings.startAngle,blocked)>0;
        float entrance=entry.update(now,visible,false);
        float endpoint=ProjectionMath.endpointOpacity(smoothed,inner,AnimationSettings.startAngle,blocked);
        float motion=ProjectionMath.onsetMotion(endpoint,entrance);
        shader.setFloatUniform("tilt",(float)Math.toRadians(ProjectionMath.effectTilt(smoothed,inner,AnimationSettings.startAngle,blocked))*motion);
        shader.setFloatUniform("crop",blocked?0:ProjectionMath.cropFraction(smoothed,inner,AnimationSettings.stretchPercent)*motion);
        paint.setAlpha(Math.round(255*ProjectionMath.onsetOpacity(endpoint)));
        int save=canvas.save();
        canvas.concat(GpuLayers.screenMatrix(canvas.getWidth(),canvas.getHeight(),inner,rotation,layers.width,layers.height));
        // The shader extends only a soft blur tail onto the stationary half.
        canvas.drawRect(0,0,layers.width,layers.height,paint);
        canvas.restoreToCount(save);postInvalidateOnAnimation();
    }
    boolean finishFlat(long now){
        angle=180;
        if(flatRequestedAt<0)flatRequestedAt=now;
        return now-flatRequestedAt<ProjectionAngleMotion.FLAT_RETURN_MS&&smoothed<175;
    }
    void follow(float target){angle=target;flatRequestedAt=-1;}
    void release() {closed=true;layers.close();}
}
