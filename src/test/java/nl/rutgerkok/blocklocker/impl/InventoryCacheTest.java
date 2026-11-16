package nl.rutgerkok.blocklocker.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import nl.rutgerkok.blocklocker.ProtectionCache;
import nl.rutgerkok.blocklocker.SearchMode;
import nl.rutgerkok.blocklocker.impl.event.InteractListener;

class InventoryCacheTest {
    @Test
    void cachedInventoryDecisionsDoNotMaterializeBlocks() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(-2);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(3);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(new Location(world, -1.1, 64.9, 3.2, 70, 80));
        HopperCacheImpl cache = new HopperCacheImpl();
        cache.setAllowed(block, ProtectionCache.CacheType.REDSTONE, true);
        BlockLockerPluginImpl plugin = mock(BlockLockerPluginImpl.class);
        when(plugin.getProtectionCache()).thenReturn(cache);
        var listener = new InteractListener(plugin);
        var event = new InventoryMoveItemEvent(inventory, mock(ItemStack.class), inventory, true);
        for (int i = 0; i < 10_000; i++) listener.onInventoryMoveItemEvent(event);
        assertFalse(event.isCancelled());
        verify(world, never()).getBlockAt(any(Location.class));
        verify(plugin, never()).getProtectionFinder();
    }

    @Test
    void sourceDenialStopsBeforeReadingDestination() {
        Block block = mock(Block.class);
        World world = mock(World.class);
        when(block.getWorld()).thenReturn(world);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        Inventory from = mock(Inventory.class), to = mock(Inventory.class);
        when(from.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        HopperCacheImpl cache = new HopperCacheImpl();
        cache.setAllowed(block, ProtectionCache.CacheType.REDSTONE, false);
        BlockLockerPluginImpl plugin = mock(BlockLockerPluginImpl.class);
        when(plugin.getProtectionCache()).thenReturn(cache);
        var event = new InventoryMoveItemEvent(from, mock(ItemStack.class), to, true);
        new InteractListener(plugin).onInventoryMoveItemEvent(event);
        assertTrue(event.isCancelled());
        verify(to, never()).getLocation();
    }

    @Test
    void oldThirdPartyCacheImplementationStillWorksOnMissAndHit() {
        class LegacyCache implements ProtectionCache {
            CacheFlag flag = CacheFlag.MISS_CACHE;
            int writes;
            public CacheFlag getAllowed(Block block, CacheType type) { return flag; }
            public void setAllowed(Block block, CacheType type, boolean allowed) {
                writes++;
                flag = allowed ? CacheFlag.ALLOWED : CacheFlag.NOT_ALLOWED;
            }
        }
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        Inventory inv = mock(Inventory.class);
        when(inv.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        LegacyCache cache = new LegacyCache();
        BlockLockerPluginImpl plugin = mock(BlockLockerPluginImpl.class);
        ProtectionFinderImpl finder = mock(ProtectionFinderImpl.class);
        when(plugin.getProtectionCache()).thenReturn(cache);
        when(plugin.getProtectionFinder()).thenReturn(finder);
        when(finder.findProtection(block, SearchMode.MAIN_BLOCKS_ONLY)).thenReturn(Optional.empty());
        var listener = new InteractListener(plugin);
        var event = new InventoryMoveItemEvent(inv, mock(ItemStack.class), inv, true);
        listener.onInventoryMoveItemEvent(event);
        listener.onInventoryMoveItemEvent(event);
        assertFalse(event.isCancelled());
        assertEquals(1, cache.writes);
        verify(finder, times(1)).findProtection(block, SearchMode.MAIN_BLOCKS_ONLY);
    }
}
