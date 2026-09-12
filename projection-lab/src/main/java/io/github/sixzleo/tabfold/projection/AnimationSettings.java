package io.github.sixzleo.tabfold.projection;
import android.content.Context;
import android.content.SharedPreferences;

/** One in-memory settings snapshot shared by UI and telemetry. */
final class AnimationSettings {
    static volatile int blurPercent=100, openAngle=60, closeAngle=120;
    private static SharedPreferences prefs;
    static synchronized void init(Context context) {
        if(prefs!=null)return;
        prefs=context.getApplicationContext().getSharedPreferences("animation_settings",0);
        blurPercent=clamp(prefs.getInt("blur",100),0,200);
        openAngle=clamp(prefs.getInt("open",60),10,170);
        closeAngle=clamp(prefs.getInt("close",120),10,170);
    }
    static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    static void blur(int v){blurPercent=clamp(v,0,200);prefs.edit().putInt("blur",blurPercent).apply();}
    static void open(int v){openAngle=clamp(v,10,170);prefs.edit().putInt("open",openAngle).apply();}
    static void close(int v){closeAngle=clamp(v,10,170);prefs.edit().putInt("close",closeAngle).apply();}
    static void reset(){blur(100);open(60);close(120);}
}
