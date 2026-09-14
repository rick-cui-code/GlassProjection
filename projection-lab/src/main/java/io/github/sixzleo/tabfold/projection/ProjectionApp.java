package io.github.sixzleo.tabfold.projection;

import android.app.*;
import android.os.Bundle;

public final class ProjectionApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity activity){UpdateCoordinator.get(ProjectionApp.this).foreground(activity);}
            public void onActivityPaused(Activity activity){UpdateCoordinator.get(ProjectionApp.this).background(activity);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        });
    }
}
