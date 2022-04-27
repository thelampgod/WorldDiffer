package com.github.thelampgod.util;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.daporkchop.lib.common.system.OperatingSystem;
import net.daporkchop.lib.common.system.PlatformInfo;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
@NoArgsConstructor
public class ProgressMonitor {
    protected static final String CONTROL_SEQUENCE = '\u001B' + "[";
    protected static final String RESET_LINE = CONTROL_SEQUENCE + "1F" + CONTROL_SEQUENCE + "1G" + CONTROL_SEQUENCE + "2K";

    protected static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    protected static final NumberFormat PERCENT_FORMAT = NumberFormat.getPercentInstance(Locale.US);

    protected static final boolean ANSI = "YES".equalsIgnoreCase(System.getenv().getOrDefault("ANSI", PlatformInfo.OPERATING_SYSTEM == OperatingSystem.Windows ? "" : "YES"));

    static {
        NUMBER_FORMAT.setMaximumFractionDigits(2);
        PERCENT_FORMAT.setMaximumFractionDigits(2);
    }

    protected long done;
    @NonNull
    protected long total;

    protected long lastTime;
    protected long lastDone;
    protected final double[] speeds = IntStream.range(0, 120).mapToDouble(i -> Double.NaN).toArray();

    public synchronized void step() {
        checkState(this.done < this.total, "already done?!?");
        this.done++;

        this.update();
    }

    public synchronized void setTotal(long total) {
        this.total = total;

        this.update();
    }

    public synchronized void set(long done, long total) {
        this.done = done;
        this.total = total;

        this.update();
    }

    private void update() {
        long now = System.nanoTime();
        if (this.done == this.total || this.lastTime + TimeUnit.SECONDS.toNanos(1L) <= now) {
            System.arraycopy(this.speeds, 0, this.speeds, 1, this.speeds.length - 1);
            this.speeds[0] = ((this.done - this.lastDone) * TimeUnit.SECONDS.toNanos(1L)) / (double) (now - this.lastTime);
            this.lastDone = this.done;
            this.lastTime = now;

            double speed = DoubleStream.of(this.speeds).filter(d -> !Double.isNaN(d)).average().getAsDouble();
            Duration eta = Duration.ofSeconds((long) ((this.total - this.done) / speed));
            System.out.printf(
                    ANSI
                            ? RESET_LINE + "processed %s/%s chunks (%s) @ %schunks/s eta %dd %02dh %02dm %02ds\n"
                            : "processed %s/%s chunks (%s) @ %schunks/s eta %dd %02dh %02dm %02ds\n",
                    NUMBER_FORMAT.format(this.done), NUMBER_FORMAT.format(this.total),
                    PERCENT_FORMAT.format((double) this.done / this.total),
                    NUMBER_FORMAT.format(speed),
                    eta.toDays(), eta.toHours() - TimeUnit.DAYS.toHours(eta.toDays()), eta.toMinutes() - TimeUnit.HOURS.toMinutes(eta.toHours()), eta.getSeconds() - TimeUnit.MINUTES.toSeconds(eta.toMinutes()));

            if (ANSI) {
                if (this.done == this.total) {
                    System.out.println();
                } else {
                    System.out.flush();
                }
            }
        }
    }
}

