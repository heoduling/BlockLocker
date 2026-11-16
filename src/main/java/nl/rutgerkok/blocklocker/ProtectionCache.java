package nl.rutgerkok.blocklocker;

import org.bukkit.block.Block;
import org.bukkit.Location;

/**
 * A simple cache that stores whether a block is protected. Entries expire after
 * a few seconds. Used for hoppers and copper golems, which do <em>a lot</em> of successive
 * checks.
 */
public interface ProtectionCache {

    enum CacheFlag {
        /**
         * Block is certainly protected.
         */
        NOT_ALLOWED,
        /**
         * Block is not protected.
         */
        ALLOWED,
        /**
         * Not cached; we don't know whether the block is protected or not.
         */
        MISS_CACHE
    }

    enum CacheType {
        /**
         * Checks whether redstone is allowed.
         */
        REDSTONE,
        /**
         * Checks whether golems are allowed.
         */
        GOLEM
    }

    /**
     * Gets whether redstone/golems are allowed at the given block.
     *
     * @param cacheType Golems or redstone.
     * @param block     The block.
     * @return Whether the given block is locked.
     */
    CacheFlag getAllowed(Block block, CacheType cacheType);

    /**
     * Reads the decision at the block coordinates of a location. Implementations
     * may avoid constructing a Block; existing implementations keep working.
     * The location is only read for this call and is never retained as a key.
     */
    default CacheFlag getAllowedAt(Location location, CacheType cacheType) {
        return getAllowed(location.getBlock(), cacheType);
    }

    /**
     * Sets whether the given block is locked for redstone.
     *
     * @param cacheType Golems or redstone.
     * @param block     The block.
     * @param allowed   True if redstone/golems are allowed. Always true if there's no protection at the location.
     */
    void setAllowed(Block block, CacheType cacheType, boolean allowed);


}
