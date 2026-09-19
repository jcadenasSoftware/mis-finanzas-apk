package com.jcadenas.xpendz.ui.loans

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentReversalGuardTest {

    @Test
    fun secondAcquireForSamePaymentIsRejected() {
        val guard = PaymentReversalGuard()

        assertTrue(guard.tryAcquire("pay-1"))
        assertFalse(guard.tryAcquire("pay-1"))
        assertFalse(guard.tryAcquire("pay-1"))
        assertTrue(guard.isInFlight("pay-1"))
    }

    @Test
    fun acquireSucceedsAgainAfterRelease() {
        val guard = PaymentReversalGuard()

        assertTrue(guard.tryAcquire("pay-1"))
        guard.release("pay-1")

        assertFalse(guard.isInFlight("pay-1"))
        assertTrue(guard.tryAcquire("pay-1"))
    }

    @Test
    fun differentPaymentsAcquireIndependently() {
        val guard = PaymentReversalGuard()

        assertTrue(guard.tryAcquire("pay-1"))
        assertTrue(guard.tryAcquire("pay-2"))

        guard.release("pay-1")
        assertFalse(guard.isInFlight("pay-1"))
        assertTrue(guard.isInFlight("pay-2"))
    }

    @Test
    fun concurrentAcquiresAllowExactlyOne() {
        val guard = PaymentReversalGuard()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val acquired = AtomicInteger(0)

        repeat(threads) {
            pool.submit {
                ready.countDown()
                start.await()
                if (guard.tryAcquire("pay-1")) {
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
