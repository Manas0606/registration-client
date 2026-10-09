package io.mosip.registration.service.rid;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

public class TimestampRidGeneratorTest {
    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();

    @Test
    public void generatesDistinctFifteenDigitIds() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        TimestampRidGenerator generator = new TimestampRidGenerator(clock::get);
        clock.incrementAndGet();
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String id = generator.generateId("10001");
            Assert.assertTrue(id.matches("[1-9][0-9]{14}"));
            Assert.assertTrue(values.add(id));
        }
        clock.incrementAndGet();
        Assert.assertTrue(values.add(generator.generateId("10001")));
    }

    @Test
    public void separateStationsHaveDifferentIds() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        TimestampRidGenerator one = new TimestampRidGenerator(clock::get);
        TimestampRidGenerator two = new TimestampRidGenerator(clock::get);
        clock.incrementAndGet();
        Assert.assertNotEquals(one.generateId("10001"), two.generateId("10002"));
    }

    @Test(expected = IllegalStateException.class)
    public void failsWhenClockGoesBackwards() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        TimestampRidGenerator generator = new TimestampRidGenerator(clock::get);
        clock.incrementAndGet();
        generator.generateId("10001");
        clock.decrementAndGet();
        generator.generateId("10001");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidStation() {
        new TimestampRidGenerator(() -> EPOCH + 1).generateId("abc");
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsStationChange() {
        AtomicLong clock = new AtomicLong(EPOCH + 100);
        TimestampRidGenerator generator = new TimestampRidGenerator(clock::get);
        clock.incrementAndGet();
        generator.generateId("10001");
        generator.generateId("10002");
    }
}
