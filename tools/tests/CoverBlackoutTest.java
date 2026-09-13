package io.github.sixzleo.tabfold.projection;

public final class CoverBlackoutTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        CoverBlackout gate=new CoverBlackout();
        gate.update(0,false,true);check(gate.token()==0,"starting on outer must not black out");
        gate.update(10,true,true);gate.update(100,false,true);
        long token=gate.token();check(token!=0,"inner to outer arms mask");
        check(!gate.contentReady(token+1)&&gate.token()==token,"stale ack cannot reveal");
        CoverLayoutReady layout=new CoverLayoutReady();
        check(!layout.update(110,token,100,"old-size",false,false,true),"OFF/transient layout");
        check(!layout.update(160,token,100,"outer-landscape",true,true,true),"first frame is not stable");
        check(!layout.update(200,token,100,"outer-landscape",true,true,true),"minimum transition window");
        check(!layout.update(450,token,100,"outer-portrait",true,true,true),"late rotation resets stability");
        check(!layout.update(570,token,100,"outer-portrait",true,true,false),"old texture cannot release");
        check(layout.update(580,token,100,"outer-portrait",true,true,true),"fresh stable final frame ready");
        check(layout.update(581,token,100,"outer-portrait",true,true,false),"ready latches until ack without new source");
        check(gate.contentReady(token)&&gate.token()==0,"content ready completes wait");
        check(gate.backing(),"content-ready notification must never remove the black base");
        // A queued animation frame may be delayed: even after readiness, transparent
        // old frames must still have black beneath them instead of the native desktop.
        gate.update(600,false,true);check(gate.token()==0&&gate.backing(),"keep black below animated frames");
        gate.update(650,false,true);check(gate.backing(),"completed wait timeout must not remove active backing");
        gate.finishBacking();check(!gate.backing(),"normal closed/held desktop ends backing");
        gate.update(700,true,true);gate.update(710,false,true);
        long next=gate.token();check(next!=token,"new handoff token");
        check(!gate.contentReady(token),"old handoff completion ignored");
        check(!layout.update(711,next,710,"outer-portrait",true,true,true),"new handoff cannot reuse readiness");
        gate.update(720,true,true);check(gate.token()==0&&!gate.backing(),"reverse to inner cancels immediately");
        gate.update(730,false,true);gate.update(740,false,false);check(gate.token()==0,"sleep or disabled cancels");
        gate.update(750,false,true);check(gate.token()==0,"wake on same outer does not rearm");
        gate.update(800,true,true);gate.update(810,false,true);
        gate.update(1709,false,true);check(gate.token()!=0,"deadline is bounded but not early");
        gate.update(1710,false,true);check(gate.token()==0&&!gate.backing(),"stalled renderer must time out at 900ms");
        gate.update(1711,false,true);check(gate.token()==0,"timeout must not rearm itself");
        check(!layout.update(1800,next,710,"outer-portrait",true,false,true),"inconsistent physical/logical size invalidates ready");
        check(!layout.update(1810,0,0,"outer-portrait",true,true,true),"no transition no reveal request");
        CoverBlackout completed=new CoverBlackout();
        completed.update(0,true,true);completed.update(10,false,true);
        long completedToken=completed.token();completed.finishBacking();
        check(completed.backing(),"normal state cannot drop the backing while layout is pending");
        completed.contentReady(completedToken);completed.update(5000,false,true);
        check(completed.backing(),"only pending waits time out; active animation keeps black base");
        System.out.println("PASS: late layout/rotation, fresh frame handshake, latched readiness, stale ack, reversal, sleep, bounded timeout and no rearm");
    }
}
