package io.github.sixzleo.tabfold.projection;

/** Keep late outer-panel size/rotation updates on the same short handoff clock. */
public final class ProjectionSceneTiming {
    private String scene="";
    private boolean initialized,previousInner;
    private long sceneAt,panelAt;
    public synchronized long observe(int width,int height,int rotation,boolean inner,long now){
        String key=width+"x"+height+":"+rotation+":"+inner;
        if(!initialized||inner!=previousInner){
            panelAt=now;previousInner=inner;initialized=true;
        }
        if(!key.equals(scene)){scene=key;sceneAt=now;}
        // Bound reuse so a later, independent outer rotation gets its own entrance.
        return !inner&&now-panelAt<=500?Math.min(panelAt,sceneAt):sceneAt;
    }
}
