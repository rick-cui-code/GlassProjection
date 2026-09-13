package io.github.sixzleo.tabfold.projection;
import android.content.Context;
import android.content.SharedPreferences;

/** One in-memory settings snapshot shared by UI and telemetry. */
final class AnimationSettings {
    static volatile int blurPercent=100, openAngle=60, closeAngle=120;
    static volatile boolean globalEnabled;
    static volatile int holdSeconds=3;
    static volatile boolean swipeRestore;
    private static SharedPreferences prefs;
    static synchronized void init(Context context) {
        if(prefs!=null)return;
        prefs=context.getApplicationContext().getSharedPreferences("animation_settings",0);
        blurPercent=clamp(prefs.getInt("blur",100),0,200);
        openAngle=clamp(prefs.getInt("open",60),10,170);
        closeAngle=clamp(prefs.getInt("close",120),10,170);
        globalEnabled=prefs.getBoolean("global",false);
        holdSeconds=clamp(prefs.getInt("hold_seconds",3),1,10);
        swipeRestore=prefs.getBoolean("swipe_restore",false);
    }
    static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    static void blur(int v){blurPercent=clamp(v,0,200);prefs.edit().putInt("blur",blurPercent).apply();}
    static void open(int v){openAngle=clamp(v,10,170);prefs.edit().putInt("open",openAngle).apply();}
    static void close(int v){closeAngle=clamp(v,10,170);prefs.edit().putInt("close",closeAngle).apply();}
    static void global(boolean value){globalEnabled=value;prefs.edit().putBoolean("global",value).apply();}
    static void hold(int value){holdSeconds=clamp(value,1,10);prefs.edit().putInt("hold_seconds",holdSeconds).apply();}
    static void swipe(boolean value){swipeRestore=value;prefs.edit().putBoolean("swipe_restore",value).apply();}
    static void reset(){blur(100);open(60);close(120);hold(3);swipe(false);}
}
