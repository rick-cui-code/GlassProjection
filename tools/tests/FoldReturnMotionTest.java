package io.github.sixzleo.tabfold.probe;

public final class FoldReturnMotionTest {
    static void near(float value,float expected){if(Math.abs(value-expected)>.00001f)throw new AssertionError(value+" != "+expected);}
    public static void main(String[] args){
        FoldReturnMotion motion=new FoldReturnMotion();
        near(motion.update(0,false),1);
        near(motion.update(3000,true),1);
        float before=1;
        for(int t=3010;t<=3220;t+=10){
            float amount=motion.update(t,true);
            if(amount<0||amount>before)throw new AssertionError("return must approach neutral");
            if(amount>=.15f)near(FoldReturnMotion.coverage(amount),1);
            before=amount;
        }
        near(before,0);near(FoldReturnMotion.coverage(before),0);
        near(motion.update(5000,false),0);
        float middle=motion.update(5080,false);
        near(middle,.5f);
        near(motion.update(5080,true),middle);
        near(motion.update(5300,true),0);
        near(motion.update(6000,false),0);near(motion.update(6160,false),1);
        near(new FoldReturnMotion().update(1,true),0);
        System.out.println("PASS: 220 ms neutral return, late fade, 160 ms resume, reversal continuity and held host restart");
    }
}
