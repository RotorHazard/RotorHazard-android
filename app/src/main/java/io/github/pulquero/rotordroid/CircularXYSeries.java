package io.github.pulquero.rotordroid;

import com.androidplot.xy.XYSeries;

public final class CircularXYSeries implements XYSeries {
    private final String title;
    private final int[] xVals;
    private final int[] yVals;
    private int head;
    private int tail;
    private int size;

    public CircularXYSeries(String title, int size) {
        this.title = title;
        this.xVals = new int[size];
        this.yVals = new int[size];
    }

    public void add(int x, int y) {
        if(size < xVals.length) {
            size++;
        } else {
            if(tail >= size) {
                tail = 0;
            }
            head = tail + 1;
            if(head >= size) {
                head = 0;
            }
        }
        xVals[tail] = x;
        yVals[tail] = y;
        tail++;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Number getX(int index) {
        return xVals[(head+index) % size];
    }

    @Override
    public Number getY(int index) {
        return yVals[(head+index) % size];
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void reset() {
        head = 0;
        tail = 0;
        size = 0;
    }
}
