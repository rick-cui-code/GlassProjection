package io.github.sixzleo.tabfold.projection;

public final class LockScreenGateTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        LockScreenGate g=new LockScreenGate();
        check(!g.visible(true,true,0,1000),"Unknown scene must not enable lock projection");
        check(g.visible(true,true,1,1020),"Confirmed lock screen");
        check(g.visible(true,true,0,1250),"Window handoff retains lock scene");
        check(!g.visible(true,true,0,2050),"Uncertainty expires");
        check(g.visible(true,true,1,2100),"Lock scene returns");
        check(!g.visible(true,false,0,2200),"Sleep never authorizes display overrides");
        check(g.visible(true,true,0,20000),"Confirmed lock survives sleep for wake handoff");
        check(!g.visible(true,true,0,20300),"Wake grace expires");
        check(g.visible(true,true,1,20400),"Lock returns after waking");
        check(!g.visible(true,true,-1,20420),"Camera or call cancels immediately");
        check(!g.visible(true,true,0,20430),"No grace after another app");
        check(g.visible(true,true,1,20500),"Lock returns again");
        check(!g.visible(false,true,0,20510),"Unlock cancels immediately");
        check(!g.visible(true,true,0,20520),"No previous lock state after unlock");
        System.out.println("PASS: lock handoff, bounded wake grace, sleep, occluding app and unlock");
    }
}
