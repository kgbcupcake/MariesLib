package dev.marie.framework.diagnostics.capture;

import com.google.gson.JsonElement;
import dev.marie.framework.api.ApiStatus;

@ApiStatus.Internal
public interface ContextCapture {

    String section();

    JsonElement capture() throws Exception;
}
