package dev.marie.framework.registry;

import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.progression.ProfileDefinition;
import dev.marie.framework.api.registry.MilestoneRegistry;
import dev.marie.framework.api.registry.ProfileRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarieApiRegistriesTest {

    @BeforeEach
    @AfterEach
    void reset() {
        MarieApiRegistries.resetForTests();
        ProfileRegistry.resetInternal();
        MilestoneRegistry.resetInternal();
    }

    private static ProfileDefinition profile(String id) {
        return ProfileDefinition.builder(id).displayName(id).build();
    }

    private static MilestoneDefinition milestone(String id) {
        return MilestoneDefinition.builder(id).valueKey("protein").cumulativeGoal(10f).build();
    }

    @Test
    void codeRegisteredEntriesSurviveRepeatedReloads() {
        ProfileRegistry.register(profile("code_profile"));
        MilestoneRegistry.register(milestone("code_milestone"));

        for (int pass = 0; pass < 3; pass++) {
            MarieApiRegistries.onDatapackApplyBegin();
            ProfileRegistry.register(profile("pack_profile"));
            MilestoneRegistry.register(milestone("pack_milestone"));
            MarieApiRegistries.onDatapackApplyEnd();

            assertNotNull(ProfileRegistry.get("code_profile"), "pass " + pass);
            assertNotNull(MilestoneRegistry.get("code_milestone"), "pass " + pass);
            assertNotNull(ProfileRegistry.get("pack_profile"), "pass " + pass);
        }
    }

    @Test
    void datapackEntriesDroppedFromThePackDoNotLinger() {
        MarieApiRegistries.onDatapackApplyBegin();
        ProfileRegistry.register(profile("pack_profile"));
        MarieApiRegistries.onDatapackApplyEnd();

        MarieApiRegistries.onDatapackApplyBegin();
        MarieApiRegistries.onDatapackApplyEnd();

        assertNull(ProfileRegistry.get("pack_profile"));
    }
}
