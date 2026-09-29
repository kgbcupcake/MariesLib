package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.marie.MarieSeasonHook;
import dev.marie.framework.api.progression.MilestoneDefinition;
import dev.marie.framework.api.progression.ProfileDefinition;
import dev.marie.framework.api.progression.TrackerMilestoneDefinition;
import dev.marie.framework.api.registry.MilestoneRegistry;
import dev.marie.framework.api.registry.ProfileRegistry;
import dev.marie.framework.api.registry.SeasonHookRegistry;
import dev.marie.framework.api.registry.TrackerMilestoneRegistry;

final class ProfileMilestoneSeasonDelegate {

    private ProfileMilestoneSeasonDelegate() {}

    static void registerTrackingProfile(ProfileDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerTrackingProfile");
        ProfileRegistry.register(definition);
    }

    static void registerMilestone(MilestoneDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerMilestone");
        MilestoneRegistry.register(definition);
    }

    static void registerTrackerMilestone(TrackerMilestoneDefinition definition) {
        MarieAPIState.assertRegistrationAllowed("registerTrackerMilestone");
        TrackerMilestoneRegistry.register(definition);
    }

    static void registerSeasonHook(MarieSeasonHook hook) {
        MarieAPIState.assertRegistrationAllowed("registerSeasonHook");
        SeasonHookRegistry.register(hook);
    }
}
