package cz.vsb.catering.presentation.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeShareLinkMapper;
import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.service.SharingService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExpiredLinkSchedulerTest {
    @Test void runsAtStartAndThenPeriodically() throws InterruptedException {
        CountDownLatch first = new CountDownLatch(1), three = new CountDownLatch(3);
        SharingService sharing = new SharingService(new FakeMenuMapper(), new FakeProjectMapper(), new FakeShareLinkMapper(),
                new DirectTransactionRunner(), Fixtures.CLOCK, "http://x") {
            @Override public int deactivateExpiredLinks() {
                first.countDown();
                three.countDown();
                if (three.getCount() == 1) throw new IllegalStateException("DB down");   // must not stop the schedule
                return 0;
            }
        };
        ExpiredLinkScheduler scheduler = new ExpiredLinkScheduler(sharing, Duration.ofMillis(20));
        scheduler.start();
        try {
            assertTrue(first.await(1, TimeUnit.SECONDS), "runs at start");
            assertTrue(three.await(2, TimeUnit.SECONDS), "runs periodically");
        } finally {
            scheduler.stop();
        }
    }
}
