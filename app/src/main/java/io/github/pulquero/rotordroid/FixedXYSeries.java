package io.github.pulquero.rotordroid;

import com.androidplot.xy.XYSeries;

public final class FixedXYSeries implements XYSeries {
    private final String title;
    private final int[] yVals;
    private final int xOffset;
    private final int xFactor;

    public FixedXYSeries(String title, int offset, int factor, int size) {
        this.title = title;
        this.yVals = new int[size];
        this.xOffset = offset;
        this.xFactor = factor;
    }

    public void set(int x, int y) {
        yVals[(x-xOffset)/xFactor] = y;
    }

    public int at(int x) {
        return yVals[(x-xOffset)/xFactor];
    }

    @Override
    public int size() {
        return yVals.length;
    }

    @Override
    public Number getX(int index) {
        return xFactor*index+xOffset;
    }

    @Override
    public Number getY(int index) {
        return yVals[index];
    }

    @Override
    public String getTitle() {
        return title;
    }
}
