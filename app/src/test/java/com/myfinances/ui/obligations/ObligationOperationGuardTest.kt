package com.jcadenas.xpendz.ui.obligations

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObligationOperationGuardTest {

    @Test
    fun secondAcquireForSameOperationIsRejected() {
        val guard = ObligationOperationGuard()

        assertTrue(guard.tryAcquire("settlement.save"))
        assertFalse(guard.tryAcquire("settlement.save"))
        assertFalse(guard.tryAcquire("settlement.save"))
        assertTrue(guard.isInFlight("settlement.save"))
    }

    @Test
    fun acquireSucceedsAgainAfterRelease() {
        val guard = ObligationOperationGuard()

        assertTrue(guard.tryAcquire("settlement.delete"))
        guard.release("settlement.delete")

        assertFalse(guard.isInFlight("settlement.delete"))
        assertTrue(guard.tryAcquire("settlement.delete"))
    }

    @Test
    fun differentOperationsAcquireIndependently() {
        val guard = ObligationOperationGuard()

        assertTrue(guard.tryAcquire("obligation.save"))
        assertTrue(guard.tryAcquire("settlement.save"))

        guard.release("obligation.save")
        assertFalse(guard.isInFlight("obligation.save"))
        assertTrue(guard.isInFlight("settlement.save"))
    }

    @Test
    fun concurrentAcquiresAllowExactlyOne() {
        val guard = ObligationOperationGuard()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val acquired = AtomicInteger(0)

        repeat(threads) {
            pool.submit {
                ready.countDown()
                start.await()
                if (guard.tryAcquire("obligation.cancel")) {
                    acquired.incrementAndGet()
                }
            }
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(1, acquired.get())
    }
}
