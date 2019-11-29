public class SimpleTest {
    int f(int pp, int tt)
    {
        <selection>
        int dd = 0;
        int xx = pp;
        int yy = 0;
        yy = xx + tt;
        dd += 35;
        int zz = pp;
        zz = 14;
        zz *= 24;
        dd *= 3;
        zz += yy - xx;
        </selection>
        return dd + xx + yy + zz;
    }
}