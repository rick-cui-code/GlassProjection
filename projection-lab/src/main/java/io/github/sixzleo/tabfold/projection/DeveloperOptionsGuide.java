package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

/** Shared first-use instructions, also reachable from the pairing window. */
final class DeveloperOptionsGuide {
    static final String XIAOMI_PATH="小米：设置 → 我的设备 → 连续点击 OS 版本，直到提示已进入开发者模式。";

    static boolean enabled(Activity activity){
        return Settings.Global.getInt(activity.getContentResolver(),Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0)!=0;
    }

    static void show(Activity activity){
        new AlertDialog.Builder(activity).setTitle("如何开启开发者选项")
            .setMessage("找不到开发者选项？请先开启开发者模式。\n\n"+XIAOMI_PATH
                +"\n\n开启后返回玻璃投影，点「配对」。在开发者选项中开启「无线调试」，再点「使用配对码配对设备」，将 6 位码输入配对小窗。已有配对记录时无需重复配对。")
            .setPositiveButton("打开系统设置",(dialog,which)->{
                try{activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
                catch(ActivityNotFoundException e){Toast.makeText(activity,"请手动打开手机设置",Toast.LENGTH_LONG).show();}
            })
            .setNegativeButton("知道了",null).show();
    }
}
