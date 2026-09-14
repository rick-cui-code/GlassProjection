package io.github.sixzleo.tabfold.projection;

/** Timers are a fallback; sensor/display/accessibility events still run immediately. */
final class ProjectionCadence {
    static long delay(boolean standby,boolean blocked,boolean fade,long coverToken,boolean pending,boolean uncertain){
        if(fade||coverToken!=0||pending||uncertain)return 40;
        if(standby)return 500;
        return blocked?200:40;
    }
}
