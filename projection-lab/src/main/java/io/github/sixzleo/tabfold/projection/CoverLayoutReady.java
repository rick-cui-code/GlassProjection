package io.github.sixzleo.tabfold.projection;

/** Geometry and fresh-source heuristic; never mistakes a reused texture for readiness. */
public final class CoverLayoutReady {
    private long token,stableAt;
    private String key="";
    private int freshFrames;
    private boolean ready;
    public boolean update(long now,long nextToken,long started,String geometry,boolean on,
                          boolean valid,boolean fresh){
        return update(now,nextToken,started,geometry,on,valid,fresh,450);
    }
    public boolean update(long now,long nextToken,long started,String geometry,boolean on,
                          boolean valid,boolean fresh,long minimumWait){
        if(nextToken==0){token=0;key="";freshFrames=0;ready=false;return false;}
        if(token!=nextToken||!key.equals(geometry)||!on||!valid){
            token=nextToken;key=geometry;stableAt=now;freshFrames=0;ready=false;
        }
        if(!on||!valid)return false;
        if(fresh)freshFrames++;
        // Late outer rotation was observed >300 ms after lighting up.
        ready=ready||(now-started>=minimumWait&&now-stableAt>=120&&freshFrames>=2&&fresh);
        return ready;
    }
}
