package io.github.sixzleo.tabfold.projection;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Xiaomi's installed-services list, with the standard settings page as a compatibility fallback. */
final class AccessibilitySettings {
    static void open(Activity activity){
        String maker=Build.MANUFACTURER;
        if("Xiaomi".equalsIgnoreCase(maker)||"Redmi".equalsIgnoreCase(maker)||"POCO".equalsIgnoreCase(maker)){
            Intent downloaded=new Intent().setClassName("com.android.settings","com.android.settings.SubSettings")
                .putExtra(":settings:show_fragment","com.android.settings.accessibility.InstalledAccessibilityService")
                .putExtra(":settings:show_fragment_title","已下载的应用");
            if(launch(activity,downloaded))return;
        }
        if(!launch(activity,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            Toast.makeText(activity,"请在系统设置中打开「辅助功能 → 已下载的应用」",Toast.LENGTH_LONG).show();
    }
    private static boolean launch(Activity activity,Intent intent){
        try{activity.startActivity(intent);return true;}
        catch(ActivityNotFoundException|SecurityException unavailable){return false;}
    }
}
