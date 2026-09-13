package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Opens the system pairing UI; never enables debugging or accepts authorization. */
final class WirelessSettings {
    static void open(Activity activity){
        if(!DeveloperOptionsGuide.enabled(activity)){DeveloperOptionsGuide.show(activity);return;}
        if(launch(activity,new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")))return;
        String maker=Build.MANUFACTURER;
        if("Xiaomi".equalsIgnoreCase(maker)||"Redmi".equalsIgnoreCase(maker)||"POCO".equalsIgnoreCase(maker)){
            // Xiaomi Settings exposes its normal subpage activity without a shell permission.
            Intent direct=new Intent().setClassName("com.android.settings","com.android.settings.SubSettings")
                .putExtra(":settings:show_fragment","com.android.settings.development.AdbWirelessDebuggingFragment")
                .putExtra(":settings:show_fragment_title","无线调试");
            if(launch(activity,direct))return;
        }
        openDeveloperOptions(activity);
    }
    static void openDeveloperOptions(Activity activity){
        if(!DeveloperOptionsGuide.enabled(activity)){DeveloperOptionsGuide.show(activity);return;}
        if(!launch(activity,new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)))DeveloperOptionsGuide.show(activity);
        else Toast.makeText(activity,"请在开发者选项中打开「无线调试」",Toast.LENGTH_LONG).show();
    }
    private static boolean launch(Activity activity,Intent intent){
        try{activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
        catch(ActivityNotFoundException|SecurityException unavailable){return false;}
    }
}
