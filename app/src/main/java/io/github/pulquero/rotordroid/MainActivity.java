package io.github.pulquero.rotordroid;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnCheckedChanged;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.androidplot.util.PixelUtils;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.FastLineAndPointRenderer;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private static final int MIN_FREQ = 5645;
    private static final int MAX_FREQ = 5945;
    private static final long SCAN_UPDATE_INTERVAL = 100L;
    private static final int SCAN_STEP = 2;
    private static final long SIGNAL_UPDATE_INTERVAL = 50L;
    private static final int NUM_SAMPLES = 200;
    private ScheduledExecutorService executor;
    private Future<Node> fNode;
    private Callable<ScheduledFuture<?>> acquisitionStarter;
    private ScheduledFuture<?> fStarter;

    private FixedXYSeries spectrumSeries;
    private FixedXYSeries minSeries;
    private FixedXYSeries maxSeries;
    private CircularXYSeries rssiSeries;
    private CircularXYSeries historySeries;
    private Redrawer redrawer;
    private long startTime;

    @BindColor(R.color.spectrum)
    int spectrumColor;
    @BindColor(R.color.min)
    int minColor;
    @BindColor(R.color.max)
    int maxColor;
    @BindColor(R.color.rssi)
    int rssiColor;
    @BindColor(R.color.history)
    int historyColor;

    @BindView(R.id.freqSelector)
    EditText freqSelector;
    @BindView(R.id.scanSwitch)
    Switch scanSwitch;
    @BindView(R.id.plot)
    XYPlot plot;
    @BindView(R.id.messages)
    TextView msgLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        plot.setRangeBoundaries(0, 150, BoundaryMode.FIXED);
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10.0);
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).getPaint().setTextSize(PixelUtils.spToPix(10.0f));
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).getPaint().setTextSize(PixelUtils.spToPix(10.0f));
        plot.getLegend().getTextPaint().setTextSize(PixelUtils.spToPix(10.0f));
        PanZoom.attach(plot);
        redrawer = new Redrawer(plot, 25, false);
        executor = Executors.newSingleThreadScheduledExecutor();
        fNode = executor.submit(() -> Node.connect(this));
        acquisitionStarter = scanAcquisition();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (rssiSeries != null) {
            rssiSeries.reset();
        }
        if (historySeries != null) {
            historySeries.reset();
        }
        startTime = SystemClock.elapsedRealtime();
        executor.execute(() -> {
            try {
                Node node = fNode.get();
                int freq = node.getFrequency();
                String freqValue = Integer.toString(freq);
                runOnUiThread(() -> freqSelector.setText(freqValue));
            } catch (ExecutionException | InterruptedException | IOException ex) {
                runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        redrawer.start();
        try {
            fStarter = acquisitionStarter.call();
        } catch (Exception ex) {
            runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fStarter != null) {
            fStarter.cancel(true);
            fStarter = null;
        }
        redrawer.pause();
        clearSeries();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Node node = fNode.get();
            node.close();
        } catch (ExecutionException | InterruptedException | IOException ignore) {
        }
        executor.shutdown();
    }

    private Callable<ScheduledFuture<?>> scanAcquisition() {
        return () -> {
            clearSeries();
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 25.0);
            plot.setDomainBoundaries(MIN_FREQ+5, MAX_FREQ+5, BoundaryMode.FIXED);
            minSeries = new FixedXYSeries("Min", MIN_FREQ, SCAN_STEP, (MAX_FREQ - MIN_FREQ)/SCAN_STEP + 1);
            plot.addSeries(minSeries, new FastLineAndPointRenderer.Formatter(minColor, null, null));
            maxSeries = new FixedXYSeries("Max", MIN_FREQ, SCAN_STEP, (MAX_FREQ - MIN_FREQ)/SCAN_STEP + 1);
            plot.addSeries(maxSeries, new FastLineAndPointRenderer.Formatter(maxColor, null, null));
            spectrumSeries = new FixedXYSeries("Live", MIN_FREQ, SCAN_STEP, (MAX_FREQ - MIN_FREQ)/SCAN_STEP + 1);
            plot.addSeries(spectrumSeries, new FastLineAndPointRenderer.Formatter(spectrumColor, null, null));
            plot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, new XYGraphWidget.LineLabelRenderer());
            return executor.scheduleWithFixedDelay(() -> {
                try {
                    Node node = fNode.get();
                    int freq = node.getFrequency();
                    LapStats stats = node.readLapStats(currentTime());

                    spectrumSeries.set(freq, stats.rssi);
                    minSeries.set(freq, minSeries.at(freq) == 0 ? stats.rssi : Math.min(stats.rssi, minSeries.at(freq)));
                    maxSeries.set(freq, Math.max(stats.rssi, maxSeries.at(freq)));
                    freq+=SCAN_STEP;
                    if (freq > MAX_FREQ) {
                        freq = MIN_FREQ;
                    }
                    node.setFrequency(freq);
                    String freqValue = Integer.toString(freq);
                    runOnUiThread(() -> freqSelector.setText(freqValue));
                } catch (ExecutionException | InterruptedException | IOException ex) {
                    runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
                }
            }, 0L, SCAN_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        };
    }

    private Callable<ScheduledFuture<?>> signalAcquisition() {
        return () -> {
            clearSeries();
            plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 1000.0);
            long time = currentTime();
            plot.setDomainBoundaries(time - NUM_SAMPLES*SIGNAL_UPDATE_INTERVAL, time, BoundaryMode.FIXED);
            rssiSeries = new CircularXYSeries("Live", NUM_SAMPLES);
            plot.addSeries(rssiSeries, new FastLineAndPointRenderer.Formatter(rssiColor, null, null));
            historySeries = new CircularXYSeries("History", NUM_SAMPLES);
            plot.addSeries(historySeries, new FastLineAndPointRenderer.Formatter(historyColor, null, null));
            plot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, new XYGraphWidget.LineLabelRenderer() {
                @Override
                protected void drawLabel(Canvas canvas, String text, Paint paint, float x, float y, boolean isOrigin) {
                }
            });
            return executor.scheduleWithFixedDelay(() -> {
                try {
                    Node node = fNode.get();
                    long currentTime = currentTime();
                    LapStats stats = node.readLapStats(currentTime);

                    rssiSeries.add(stats.t, stats.rssi);
                    if(stats.historyRssi != 0) {
                        historySeries.add(stats.t - stats.msSinceHistoryStart, stats.historyRssi);
                        if (stats.msSinceHistoryStart != stats.msSinceHistoryEnd) {
                            historySeries.add(stats.t - stats.msSinceHistoryEnd, stats.historyRssi);
                        }
                    }
                    runOnUiThread(() ->
                        plot.setDomainBoundaries(currentTime - NUM_SAMPLES*SIGNAL_UPDATE_INTERVAL, currentTime, BoundaryMode.FIXED)
                    );
                } catch (ExecutionException | InterruptedException | IOException ex) {
                    runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
                }
            }, 0L, SIGNAL_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        };
    }

    private int currentTime() {
        return (int) (SystemClock.elapsedRealtime() - startTime);
    }

    @OnCheckedChanged(R.id.scanSwitch)
    public void onScanSwitch() {
        fStarter.cancel(true);
        if(scanSwitch.isChecked()) {
            freqSelector.setEnabled(false);
            freqSelector.removeTextChangedListener(updateFrequencyListener);
            acquisitionStarter = scanAcquisition();
        } else {
            freqSelector.addTextChangedListener(updateFrequencyListener);
            freqSelector.setEnabled(true);
            acquisitionStarter = signalAcquisition();
        }
        clearSeries();
        try {
            fStarter = acquisitionStarter.call();
        } catch (Exception ex) {
            runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
        }
    }

    private void clearSeries() {
        if (spectrumSeries != null) {
            plot.removeSeries(spectrumSeries);
            spectrumSeries = null;
        }
        if (minSeries != null) {
            plot.removeSeries(minSeries);
            minSeries = null;
        }
        if (maxSeries != null) {
            plot.removeSeries(maxSeries);
            maxSeries = null;
        }
        if (rssiSeries != null) {
            plot.removeSeries(rssiSeries);
            rssiSeries = null;
        }
        if (historySeries != null) {
            plot.removeSeries(historySeries);
            historySeries = null;
        }
    }

    final TextWatcher updateFrequencyListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            String freqValue = editable.toString();
            if (freqValue.isEmpty()) {
                return;
            }
            int freq = Integer.parseInt(freqValue);
            if (freq >= MIN_FREQ && freq <= MAX_FREQ) {
                if(rssiSeries != null) {
                    plot.removeSeries(rssiSeries);
                }
                if (historySeries != null) {
                    plot.removeSeries(historySeries);
                }
                executor.execute(() -> {
                    try {
                        Node node = fNode.get();
                        node.setFrequency(freq);
                        if (rssiSeries != null) {
                            rssiSeries.reset();
                        }
                        if (historySeries != null) {
                            historySeries.reset();
                        }
                    } catch (ExecutionException | InterruptedException | IOException ex) {
                        runOnUiThread(() -> msgLabel.setText(ex.getMessage()));
                    }
                });
                if(rssiSeries != null) {
                    plot.addSeries(rssiSeries, new FastLineAndPointRenderer.Formatter(rssiColor, null, null));
                }
                if (historySeries != null) {
                    plot.addSeries(historySeries, new FastLineAndPointRenderer.Formatter(historyColor, null, null));
                }
            }
        }
    };
}
