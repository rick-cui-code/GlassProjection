package io.github.sixzleo.tabfold.projection;

import java.util.Set;

public final class AppBlacklistTest {
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    public static void main(String[] args){
        AppBlacklist gate=new AppBlacklist();Set<String> excluded=Set.of("example.video","example.game");
        check(!gate.blocked(excluded),"empty startup");
        gate.observe("example.video");check(gate.blocked(excluded),"selected app excluded");
        gate.observe(null);gate.observe("");check(gate.blocked(excluded),"missing handoff/keyboard root retains exclusion");
        gate.observe("example.videoplayer");check(!gate.blocked(excluded),"exact package matching");
        gate.observe("example.game");check(gate.blocked(excluded),"second selection");
        check(!gate.blocked(Set.of()),"removal takes effect without foreground change");
        gate.observe("com.android.systemui");check(!gate.blocked(excluded),"real lock screen can animate");
        gate.observe("example.home");check(!gate.blocked(excluded),"return to launcher restores");
        check(gate.blocked(Set.of("example.home")),"launcher can also be excluded");
        ScreenFade fade=new ScreenFade();fade.update(0,0,false,true,false);
        fade.update(100,56,false,true,false);check(fade.active(),"start a fade");
        gate.observe("example.video");fade.update(110,58,false,!gate.blocked(excluded),false);
        check(!fade.active()&&!fade.backing()&&fade.darkness()==0&&fade.token()==0,"entering blacklist clears all fade layers");
        gate.observe("example.home");fade.update(200,0,false,!gate.blocked(excluded),false);
        fade.update(300,56,false,true,false);check(fade.active(),"leaving blacklist restores next fold");
        System.out.println("PASS: exact multi-selection, transient unknown roots, removal, lock/home restore and fade cancellation");
    }
}
