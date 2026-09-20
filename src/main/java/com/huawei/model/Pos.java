package com.huawei.model;

/**
 * 坐标类。所有距离均采用切比雪夫距离：max(|x1-x0|, |y1-y0|)。
 */
public class Pos {

    public int x;
    public int y;

    public Pos() {
    }

    public Pos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** 与另一坐标的切比雪夫距离 */
    public int chebyshevTo(Pos other) {
        return chebyshevTo(other.x, other.y);
    }

    /** 与给定坐标的切比雪夫距离 */
    public int chebyshevTo(int ox, int oy) {
        return Math.max(Math.abs(x - ox), Math.abs(y - oy));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Pos pos = (Pos) o;
        return x == pos.x && y == pos.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
