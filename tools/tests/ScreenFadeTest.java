package io.github.sixzleo.tabfold.projection;

import io.github.sixzleo.tabfold.probe.EarlyDisplayModel;

public final class ScreenFadeTest {
    private static void near(float a,float b){if(Math.abs(a-b)>.00001f)throw new AssertionError(a+" != "+b);}
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int open:new int[]{10,60,170})for(int close:new int[]{10,120,170}){
            ScreenFade fade=new ScreenFade();fade.configure(open,close);
            EarlyDisplayModel controller=new EarlyDisplayModel();controller.configure(open,close);
            fade.update(0,0,false,true,false);near(fade.darkness(),0);
            controller.update(0,true,false);
            float last=0;long now=0;
            for(int angle=1;angle<=open;angle++){
                now=angle*20;fade.update(now,angle,false,true,false);
                int requested=controller.update(angle,true,false);
                if(angle<open)check(requested!=2,"fade must not move actual inner switch threshold");
                else check(requested==2,"actual switch threshold must remain configured");
                check(fade.darkness()>=last,"pre-fade must darken monotonically");last=fade.darkness();
            }
            near(last,1);check(fade.token()==0,"preview must precede actual panel change");
            fade.update(now+20,open+1,true,true,false);long token=fade.token();
            check(token!=0&&fade.backing(),"inner handoff must also wait on black");near(fade.darkness(),1);
            check(!fade.contentReady(token+1,now+30),"stale completion ignored");
            check(fade.contentReady(token,now+200),"inner fresh frame begins reveal");
            near(fade.darkness(),1);
            near(ScreenFade.sample(now+290,fade.mode(),fade.phaseStartedAt(),fade.darkness(),fade.cancelFrom()),.5f);
            fade.update(now+290,open+2,true,true,false);near(fade.darkness(),.5f);
            fade.update(now+380,open+3,true,true,false);near(fade.darkness(),0);
            check(!fade.active()&&!fade.backing(),"completed reveal releases backing only at clear endpoint");
            // Begin a normal closing gesture from flat, using the independent close threshold.
            now+=500;fade.update(now,180,true,true,false);controller.update(180,true,true);
            last=0;
            for(int angle=179;angle>=close;angle--){
                now+=20;fade.update(now,angle,true,true,false);
                int requested=controller.update(angle,true,true);
                if(angle>close&&angle<175)check(requested!=0,"fade must not move actual outer switch threshold");
                if(angle==close)check(requested==0,"configured outer threshold unchanged");
                check(fade.darkness()>=last,"closing pre-fade monotonic");last=fade.darkness();
            }
            near(last,1);fade.update(now+20,close-1,false,true,false);
            long outer=fade.token();check(outer!=0&&outer!=token,"outer handoff has a fresh token");
            fade.update(now+919,close-1,false,true,false);near(fade.darkness(),1);
            fade.update(now+920,close-1,false,true,false);near(fade.darkness(),1);
            check(fade.token()==0,"timeout ends wait by revealing, not a hard cut");
            fade.update(now+1010,close-1,false,true,false);near(fade.darkness(),.5f);
            fade.update(now+1100,close-1,false,true,false);near(fade.darkness(),0);
        }
        ScreenFade reverse=new ScreenFade();reverse.update(0,0,false,true,false);
        reverse.update(100,54,false,true,false);near(reverse.darkness(),.5f);
        reverse.update(120,51,false,true,false);near(reverse.darkness(),.5f);
        reverse.update(180,51,false,true,false);near(reverse.darkness(),.25f);
        reverse.update(240,51,false,true,false);near(reverse.darkness(),0);
        ScreenFade hold=new ScreenFade();hold.update(0,0,false,true,false);
        hold.update(100,54,false,true,false);hold.update(110,54,false,true,true);
        hold.update(230,54,false,true,true);near(hold.darkness(),0);
        hold.update(240,54,false,false,false);check(!hold.active()&&!hold.backing(),"scope/sleep cancels all layers");
        ScreenFade slow=new ScreenFade();slow.update(0,0,false,true,false);
        slow.update(5000,54,false,true,false);slow.update(10000,54,false,true,false);
        near(slow.darkness(),.5f);check(slow.token()==0,"slow movement before threshold must not time out");
        ScreenFade missing=new ScreenFade();missing.update(0,0,false,true,false);
        missing.update(100,61,false,true,false);missing.update(1000,61,false,true,false);
        missing.update(1120,61,false,true,false);near(missing.darkness(),0);
        missing.update(2000,61,false,true,false);near(missing.darkness(),0);
        CoverLayoutReady layout=new CoverLayoutReady();
        check(!layout.update(0,1,0,"inner",true,true,true,120),"inner needs fresh stable frames");
        check(layout.update(120,1,0,"inner",true,true,true,120),"inner may reveal after its short stable window");
        System.out.println("PASS: both directions, configured threshold extremes, fade before actual switch, continuous reveal, timeout/reversal/hold and slow-gesture handling");
    }
}
