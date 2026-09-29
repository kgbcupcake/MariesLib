package dev.marie.framework.network;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public enum SyncState {
    UNINITIALIZED,
    PENDING,
    ACTIVE
}
