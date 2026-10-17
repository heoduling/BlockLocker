package nl.rutgerkok.blocklocker.impl;

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import nl.rutgerkok.blocklocker.ChestSettings;
import nl.rutgerkok.blocklocker.ProfileFactory;
import nl.rutgerkok.blocklocker.ProtectionFinder;
import nl.rutgerkok.blocklocker.SearchMode;
import nl.rutgerkok.blocklocker.impl.event.GolemListener;
import nl.rutgerkok.blocklocker.profile.Profile;
import nl.rutgerkok.blocklocker.protection.Protection;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemListenerTest {
    @Test
    void unprotectedContainerRemainsAllowedOnCacheHit() {
        checkRepeatedTarget(Optional.empty(), true);
    }

    @Test
    void privateContainerRemainsDeniedOnCacheHit() {
        checkRepeatedTarget(Optional.of(false), false);
    }

    @Test
    void authorizedContainerRemainsAllowedOnCacheHit() {
        checkRepeatedTarget(Optional.of(true), true);
    }

    private void checkRepeatedTarget(Optional<Boolean> protectionAllowsGolem, boolean expectedAllowed) {
        BlockLockerPluginImpl plugin = mock(BlockLockerPluginImpl.class);
        Block block = mock(Block.class);
        Entity golem = mock(Entity.class);
        ProtectionFinder finder = mock(ProtectionFinder.class);
        when(plugin.getChestSettings()).thenReturn(mock(ChestSettings.class));
        // Exercise the production cache: the second event must observe the first event's decision.
        when(plugin.getProtectionCache()).thenReturn(new HopperCacheImpl());
        when(plugin.getProtectionFinder()).thenReturn(finder);
        Optional<Protection> protection = protectionAllowsGolem.map(allowed -> {
            Protection result = mock(Protection.class);
            Profile profile = mock(Profile.class);
            ProfileFactory profiles = mock(ProfileFactory.class);
            when(plugin.getProfileFactory()).thenReturn(profiles);
            when(profiles.fromGolem()).thenReturn(profile);
            when(result.isAllowed(profile)).thenReturn(allowed);
            return result;
        });
        when(finder.findProtection(block, SearchMode.MAIN_BLOCKS_ONLY)).thenReturn(protection);
        GolemListener listener = new GolemListener(plugin);
        for (int attempt = 1; attempt <= 2; attempt++) {
            ItemTransportingEntityValidateTargetEvent event =
                    new ItemTransportingEntityValidateTargetEvent(golem, block);
            listener.onEntityContainerTarget(event);
            assertEquals(expectedAllowed, event.isAllowed(), "Target check " + attempt);
        }
    }
}
