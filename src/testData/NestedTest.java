public class NestedTest {
    int g(int pp) {
        int xx = 0;
        int yy = 0;
        for (int i = 0; i < 10; ++i) {
            <selection>
            if (pp % 2 == 0) {
                if (pp < 5) {
                    xx += 3;
                    yy += 4;
                    //continue;
                } else {
                    xx += 5;
                    yy += 7;
                    //break;
                }
                if (pp == 2) {
                    xx += 25;
                }
            } else {
                if (pp < 6) {
                    xx += 7;
                    yy += 10;
                    //throw new NullPointerException();
                } else {
                    xx += 9;
                    yy += 13;
                    //return xx;
                }
                if (pp == 33) {
                    yy += 13;
                }
            }
            </selection>
        }
        return xx + yy;
    }
}