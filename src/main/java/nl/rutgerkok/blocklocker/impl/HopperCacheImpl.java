package nl.rutgerkok.blocklocker.impl;

import java.util.concurrent.TimeUnit;

import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.World;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import nl.rutgerkok.blocklocker.ProtectionCache;

final class HopperCacheImpl implements ProtectionCache {

    private static final long EXPIRE_TIME_SECONDS = 10;
    private record BlockKey(World world, int x, int y, int z) { }

    private final Cache<BlockKey, Boolean> redstoneCache;
    private final Cache<BlockKey, Boolean> golemCache;

    HopperCacheImpl() {
        redstoneCache = Caffeine.newBuilder().initialCapacity(1000)
                .maximumSize(5000)
                .expireAfterWrite(EXPIRE_TIME_SECONDS, TimeUnit.SECONDS)
                .build();
        golemCache = Caffeine.newBuilder().initialCapacity(1000)
                .maximumSize(5000)
                .expireAfterWrite(EXPIRE_TIME_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private Cache<BlockKey, Boolean> getCache(CacheType cacheType) {
        return switch (cacheType) {
            case REDSTONE -> this.redstoneCache;
            case GOLEM -> this.golemCache;
        };
    }

    @Override
    public CacheFlag getAllowed(Block block, CacheType cacheType) {
        return getAllowed(new BlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ()), cacheType);
    }

    @Override
    public CacheFlag getAllowedAt(Location location, CacheType cacheType) {
        return getAllowed(new BlockKey(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ()), cacheType);
    }

    private CacheFlag getAllowed(BlockKey key, CacheType cacheType) {
        Boolean isAllowed = getCache(cacheType).getIfPresent(key);

        if (isAllowed == null) {
            return CacheFlag.MISS_CACHE;
        }
        if (isAllowed) {
            return CacheFlag.ALLOWED;
        } else {
            return CacheFlag.NOT_ALLOWED;
        }
    }

    @Override
    public void setAllowed(Block block, CacheType cacheType, boolean allowed) {
        getCache(cacheType).put(new BlockKey(block.getWorld(), block.getX(), block.getY(), block.getZ()), allowed);
    }
}
