package io.github.sixzleo.tabfold.projection;

import java.util.Set;

/** Keep the last application across missing roots during panel handoff or keyboard focus. */
public final class AppBlacklist {
    private volatile String foreground="";
    public void observe(String packageName){
        if(packageName!=null&&!packageName.isEmpty())foreground=packageName;
    }
    public String foreground(){return foreground;}
    public boolean blocked(Set<String> packages){return packages.contains(foreground);}
}
