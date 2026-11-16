package nl.rutgerkok.blocklocker.impl;

import static org.junit.jupiter.api.Assertions.*;
import static nl.rutgerkok.blocklocker.ProtectionCache.CacheFlag.*;
import static nl.rutgerkok.blocklocker.ProtectionCache.CacheType.*;
import static org.mockito.Mockito.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class HopperCacheTest {
    private static volatile Object sink;

    private Block block(World world, int x) {
        return (Block) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> 64;
                    case "getZ" -> 3;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void locationKeysShareBlockDecisionsAndSnapshotCoordinates() {
        HopperCacheImpl cache = new HopperCacheImpl();
        World world = mock(World.class), other = mock(World.class);
        cache.setAllowed(block(world, -2), REDSTONE, true);
        Location location = new Location(world, -1.1, 64.8, 3.9, 50, 80);
        assertEquals(ALLOWED, cache.getAllowedAt(location, REDSTONE));
        location.setX(100);
        assertEquals(MISS_CACHE, cache.getAllowedAt(location, REDSTONE));
        assertEquals(ALLOWED, cache.getAllowed(block(world, -2), REDSTONE));
        assertEquals(MISS_CACHE, cache.getAllowed(block(other, -2), REDSTONE));
        assertEquals(MISS_CACHE, cache.getAllowed(block(world, -2), GOLEM));
    }

    @Test
    void eachCacheStaysBoundedAfterMaintenance() throws Exception {
        HopperCacheImpl cache = new HopperCacheImpl();
        World world = mock(World.class);
        for (int i = 0; i < 10_000; i++) {
            cache.setAllowed(block(world, i), REDSTONE, true);
            cache.setAllowed(block(world, i), GOLEM, false);
        }
        for (String name : new String[]{"redstoneCache", "golemCache"}) {
            var field = HopperCacheImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            Object internal = field.get(cache);
            field.getType().getMethod("cleanUp").invoke(internal);
            var map = (java.util.Map<?, ?>) field.getType().getMethod("asMap").invoke(internal);
            assertTrue(map.size() <= 5000, name + " size=" + map.size());
        }
    }

    @Test
    void concurrentWritersAndReadersKeepIndependentKeysAndSharedHits() throws Exception {
        HopperCacheImpl cache = new HopperCacheImpl();
        World world = mock(World.class);
        Block shared = block(world, 100);
        cache.setAllowed(shared, REDSTONE, true);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                Block key = block(world, t);
                jobs.add(pool.submit(() -> {
                    for (int i = 0; i < 20_000; i++) {
                        boolean allowed = (i & 1) == 0;
                        cache.setAllowed(key, GOLEM, allowed);
                        assertEquals(allowed ? ALLOWED : NOT_ALLOWED, cache.getAllowed(key, GOLEM));
                        assertEquals(ALLOWED, cache.getAllowed(shared, REDSTONE));
                    }
                }));
            }
            for (Future<?> job : jobs) job.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void readsDoNotExtendTenSecondWriteExpiry() throws Exception {
        HopperCacheImpl cache = new HopperCacheImpl();
        Block block = block(mock(World.class), 1);
        cache.setAllowed(block, REDSTONE, true);
        long start = System.nanoTime();
        while (System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(10_150)) {
            cache.getAllowed(block, REDSTONE);
            Thread.sleep(10);
        }
        assertEquals(MISS_CACHE, cache.getAllowed(block, REDSTONE));
    }

    @Test
    void decisionsAreSeparateAndWritesReplaceExistingDecisions() {
        HopperCacheImpl cache = new HopperCacheImpl();
        Block block = mock(Block.class);
        assertEquals(MISS_CACHE, cache.getAllowed(block, REDSTONE));
        cache.setAllowed(block, REDSTONE, true);
        cache.setAllowed(block, GOLEM, false);
        assertEquals(ALLOWED, cache.getAllowed(block, REDSTONE));
        assertEquals(NOT_ALLOWED, cache.getAllowed(block, GOLEM));
        cache.setAllowed(block, REDSTONE, false);
        cache.setAllowed(block, GOLEM, true);
        assertEquals(NOT_ALLOWED, cache.getAllowed(block, REDSTONE));
        assertEquals(ALLOWED, cache.getAllowed(block, GOLEM));
    }

    @Test
    void cacheHitMaintenanceDoesNotAllocateAQueueNodePerRead() throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        HopperCacheImpl cache = new HopperCacheImpl();
        cache.setAllowed(mock(Block.class), REDSTONE, true);
        var field = HopperCacheImpl.class.getDeclaredField("redstoneCache");
        field.setAccessible(true);
        Object internal = field.get(cache);
        var map = (java.util.concurrent.ConcurrentMap<?, ?>) field.getType().getMethod("asMap").invoke(internal);
        Object key = map.keySet().iterator().next();
        var hit = MethodHandles.publicLookup().findVirtual(field.getType(), "getIfPresent",
                MethodType.methodType(Object.class, Object.class)).bindTo(internal).bindTo(key);
        for (int i = 0; i < 200_000; i++) sink = (Object) hit.invokeExact();
        long thread = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 200_000; i++) sink = (Object) hit.invokeExact();
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(bytes < 200_000L * 4, "Hot-cache maintenance allocated " + bytes / 200_000.0 + " B/read");
    }
}
