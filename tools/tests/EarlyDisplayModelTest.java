package io.github.sixzleo.tabfold.probe;
public final class EarlyDisplayModelTest {
    private static void sequence(float[] angles,int[] expected,boolean initiallyInner) {
        sequence(angles,expected,initiallyInner,60,120);
    }
    private static void sequence(float[] angles,int[] expected,boolean initiallyInner,int open,int close) {
        EarlyDisplayModel model=new EarlyDisplayModel();
        model.configure(open,close);
        boolean inner=initiallyInner;
        for(int i=0;i<angles.length;i++) {
            int actual=model.update(angles[i],true,inner);
            if(actual!=expected[i]) throw new AssertionError("step="+i+" angle="+angles[i]+" expected="+expected[i]+" actual="+actual);
            if(actual>=0) inner=actual==2;
            else if(angles[i]<=3) inner=false;
            else if(angles[i]>=175) inner=true;
        }
    }
    public static void main(String[] args) {
        sequence(new float[]{0,4,30,59.99f,60,70,90,150,175,174,130,120.01f,120,119,60,20,3},
            new int[]{-1,0,0,0,2,2,2,2,-1,2,2,2,0,0,0,0,-1},false);
        sequence(new float[]{0,60,70,69,70,68.1f,69,70,67,66,67,68,69},
            new int[]{-1,2,2,2,2,2,2,2,0,0,0,0,2},false);
        sequence(new float[]{180,121,120,119,120,120.9f,119,100,101,102,103},
            new int[]{-1,2,0,0,0,0,0,0,0,0,2},true);
        sequence(new float[]{0,30,50,48,46,20,3},new int[]{-1,0,0,0,0,0,-1},false);
        sequence(new float[]{0,4,60,89,90,150,175,160,100,91,90,89,20,3},
            new int[]{-1,0,0,0,2,2,-1,2,2,2,0,0,0,-1},false,90,90);
        sequence(new float[]{0,4,30,99,100,120,117,60,41,40,39,42,100},
            new int[]{-1,0,0,0,2,2,2,2,2,0,0,0,2},false,100,40);
        sequence(new float[]{0,4,9,10,175,174,171,170,3},
            new int[]{-1,0,0,2,-1,2,2,0,-1},false,-1,999);
        EarlyDisplayModel changed=new EarlyDisplayModel();changed.update(0,true,false);changed.update(70,true,false);
        changed.configure(90,110);
        if(changed.update(70,true,true)!=2||changed.update(69,true,true)!=2)throw new AssertionError("settings change must not invent a reversal");
        EarlyDisplayModel m=new EarlyDisplayModel();
        if(m.update(70,true,true)!=2 || m.update(70,true,true)!=2) throw new AssertionError("initial panel must hold");
        if(m.update(70,false,true)!=-1 || m.update(Float.NaN,true,true)!=-1
            || m.update(Float.POSITIVE_INFINITY,true,true)!=-1) throw new AssertionError("must release");
        if(m.update(70,true,false)!=0 || m.update(70,true,false)!=0) throw new AssertionError("lease reset loses old direction");
        for(int pass=0;pass<2;pass++) for(int i=0;i<=180;i++) {
            int state=m.update(pass==0?i:180-i,true,pass!=0);
            if(state!=-1 && state!=0 && state!=2) throw new AssertionError("dual-screen state forbidden");
        }
        System.out.println("PASS: defaults, configurable/equal/reversed thresholds, limits, settings reset, reversal, jitter, endpoints and lease reset");
    }
}
