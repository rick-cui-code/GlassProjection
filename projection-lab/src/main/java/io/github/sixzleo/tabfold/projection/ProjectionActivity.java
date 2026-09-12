package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** No accessibility, overlay, capture, network, or pose sensors. */
public final class ProjectionActivity extends Activity implements SensorEventListener {
    private SensorManager sensors;
    private ProjectionRenderer renderer;
    private Scene scene;
    private TextView status;
    private boolean running,manual,enabled=true,diagnostic,destroyed;
    private float angle=180,smoothed=180;
    private long lastFrame,reportAt;
    private int frames;
    private final Handler handler=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams params=getWindow().getAttributes();
        params.preferredRefreshRate=120;
        params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(params);
        sensors=getSystemService(SensorManager.class);
        FrameLayout root=new FrameLayout(this);scene=new Scene();root.addView(scene);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(24,12,24,12);controls.setBackgroundColor(0xc0182838);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("正在准备参考图像…");controls.addView(status);
        LinearLayout buttons=new LinearLayout(this);
        Button follow=new Button(this);follow.setText("跟随铰链");
        follow.setOnClickListener(v->{manual=false;follow.setText("跟随铰链");});buttons.addView(follow);
        Button compare=new Button(this);compare.setText("原图对照");
        compare.setOnClickListener(v->{enabled=!enabled;compare.setText(enabled?"原图对照":"恢复投影");});buttons.addView(compare);
        Button debug=new Button(this);debug.setText("距离图");
        debug.setOnClickListener(v->{diagnostic=!diagnostic;debug.setText(diagnostic?"恢复图像":"距离图");});buttons.addView(debug);
        controls.addView(buttons);
        SeekBar seek=new SeekBar(this);seek.setMax(180);seek.setProgress(180);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int progress,boolean user) { if(user) { manual=true;angle=progress;follow.setText("手动预览"); } }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });controls.addView(seek);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(controls,bottom);
        controls.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            controls.setPadding(24+bars.left,12,24+bars.right,12+bars.bottom);return insets;
        });
        if(getIntent().hasExtra("angle")) { manual=true;angle=getIntent().getFloatExtra("angle",120);smoothed=angle;seek.setProgress((int)angle); }
        if(getIntent().getBooleanExtra("clean",false)) controls.setVisibility(View.GONE);
        enabled=!getIntent().getBooleanExtra("baseline",false);
        setContentView(root);
        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        new Thread(()->{
            try {
                ProjectionRenderer ready=new ProjectionRenderer(this);
                handler.post(()->{if(destroyed)ready.close();else {renderer=ready;android.util.Log.i("ReferencePlane","READY eight cached linear-light layers; hinge only");scene.invalidate();}});
            } catch(Exception error) {
                android.util.Log.e("ReferencePlane","renderer failed",error);
                handler.post(()->{if(!destroyed)status.setText("投影加载失败："+error.getMessage());});
            }
        },"projection-assets").start();
    }
    @Override protected void onResume() {
        super.onResume();running=true;lastFrame=0;
        Sensor hinge=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
        if(hinge!=null) sensors.registerListener(this,hinge,20000);
        scene.postInvalidateOnAnimation();
    }
    @Override protected void onPause() { running=false;sensors.unregisterListener(this);super.onPause(); }
    @Override protected void onDestroy() { destroyed=true;if(renderer!=null)renderer.close();super.onDestroy(); }
    public void onSensorChanged(SensorEvent e) { if(!manual && Float.isFinite(e.values[0]))angle=e.values[0]; }
    public void onAccuracyChanged(Sensor sensor,int accuracy) {}
    private final class Scene extends View {
        Scene() { super(ProjectionActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xff142536);
            Display display=getDisplay();Display.Mode mode=display.getMode();
            boolean inner=Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight())>=1600;
            long now=SystemClock.uptimeMillis();
            float mix=lastFrame==0?1:(float)(1-Math.exp(-(now-lastFrame)/28.0));
            float target=!manual && angle>=175?180:angle;
            smoothed+=(target-smoothed)*mix;lastFrame=now;
            if(renderer!=null) {
                renderer.draw(canvas,inner,display.getRotation(),smoothed,enabled,diagnostic);
                frames++;
                if(now-reportAt>=1000) {
                    status.setText(String.format(Locale.ROOT,"参考平面 · %s · %.0f° · %s",inner?"内屏":"外屏",angle,manual?"手动":"铰链"));
                    if(reportAt>0) android.util.Log.i("ReferencePlane","draw fps="+(frames*1000f/(now-reportAt))+" angle="+angle+" inner="+inner+" rotation="+display.getRotation());
                    reportAt=now;frames=0;
                }
            }
            if(running) postInvalidateOnAnimation();
        }
    }
}
