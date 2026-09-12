package io.github.sixzleo.tabfold.projection;

public final class ProjectionMathTest {
    private static void near(double actual,double expected) {
        if(Math.abs(actual-expected)>1e-8) throw new AssertionError(actual+" != "+expected);
    }
    public static void main(String[] args) {
        for(boolean inner:new boolean[]{false,true}) {
            for(int i=0;i<=100;i++) {
                double x=(inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100;
                double[] q=ProjectionMath.project(x,.045,inner?180:0,inner);
                near(q[0],x);near(q[1],.045);near(q[2],0);
            }
            for(int angle=0;angle<=180;angle++) {
                double[] edge=ProjectionMath.project(0,.052,angle,inner);
                near(edge[0],0);near(edge[1],.052);near(edge[2],0);
                double previous=-1;
                for(int i=0;i<=100;i++) {
                    double[] q=ProjectionMath.project((inner?-1:1)*ProjectionMath.LEAF_WIDTH*i/100,.025,angle,inner);
                    if(!Double.isFinite(q[0]) || !Double.isFinite(q[1]) || q[2]+1e-8<previous) throw new AssertionError("invalid/nonmonotonic gap");
                    previous=q[2];
                }
                double[] right=ProjectionMath.project(.043,.071,angle,true);
                near(right[0],.043);near(right[1],.071);near(right[2],0);
            }
        }
        System.out.println("PASS: clear endpoints, anchored edges, fixed inner right, finite and monotonic distance at every angle");
    }
}
