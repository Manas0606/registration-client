package io.mosip.registration.service.rid;

import java.time.Instant;
import java.util.function.LongSupplier;
import org.springframework.stereotype.Service;

@Service
public class TimestampRidGenerator {
    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();
    private static final long OFFSET = 100_000_000L;
    private static final int PER_SECOND = 10;
    private final LongSupplier clock;
    private String station;
    private long lastSecond;
    private int sequence;

    public TimestampRidGenerator() {
        this(() -> System.currentTimeMillis() / 1000);
    }

    TimestampRidGenerator(LongSupplier clock) {
        this.clock = clock;
        this.lastSecond = clock.getAsLong();
        this.sequence = PER_SECOND;
    }

    public synchronized String generateId(String stationId) {
        if (stationId == null || !stationId.matches("[0-9]{5}")) {
            throw new IllegalArgumentException("Station ID must contain five digits");
        }
        if (station == null) {
            station = stationId;
        } else if (!station.equals(stationId)) {
            throw new IllegalStateException("Station changed during RID generation");
        }
        long now = clock.getAsLong();
        if (now < lastSecond) {
            throw new IllegalStateException("System clock moved backwards");
        }
        if (now == lastSecond && sequence >= PER_SECOND) {
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for RID", e);
                }
                now = clock.getAsLong();
                if (now < lastSecond) {
                    throw new IllegalStateException("System clock moved backwards");
                }
            } while (now <= lastSecond);
        }
        if (now > lastSecond) {
            lastSecond = now;
            sequence = 0;
        }
        long timePart = OFFSET + lastSecond - EPOCH;
        if (timePart < OFFSET || timePart > 999_999_999L) {
            throw new IllegalStateException("Time is outside supported RID range");
        }
        long value = timePart * 1_000_000L + Long.parseLong(stationId) * 10 + sequence++;
        return Long.toString(value);
    }
}
