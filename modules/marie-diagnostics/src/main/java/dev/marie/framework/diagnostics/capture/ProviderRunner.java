package dev.marie.framework.diagnostics.capture;

import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProvider;
import dev.marie.framework.diagnostics.api.DiagnosticSection;
import dev.marie.framework.diagnostics.api.ProviderContext;
import dev.marie.framework.diagnostics.service.DiagnosticsGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@ApiStatus.Internal
public final class ProviderRunner {

    public static final int MAX_PROVIDERS_PER_INCIDENT = 16;

    public record Target(String modId, String providerId, DiagnosticProvider provider) {
        public String key() {
            return modId + ":" + providerId;
        }
    }

    public record Outcome(JsonObject sections, List<String> failures) {}

    private final long timeoutMs;
    private final ThreadPoolExecutor pool;

    public ProviderRunner(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        AtomicInteger count = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), r -> {
            Thread t = new Thread(r, "marieslib-diagnostics-provider-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.pool.allowCoreThreadTimeOut(true);
    }

    public Outcome run(List<Target> targets, ProviderContext context) {
        JsonObject sections = new JsonObject();
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Target target = targets.get(i);
            if (i >= MAX_PROVIDERS_PER_INCIDENT) {
                sections.add(target.key(), status("skipped", 0, "provider limit of "
                        + MAX_PROVIDERS_PER_INCIDENT + " per incident reached"));
                failures.add(target.key() + ": skipped (provider limit)");
                continue;
            }
            sections.add(target.key(), runOne(target, context, failures));
        }
        return new Outcome(sections, failures);
    }

    private JsonObject runOne(Target target, ProviderContext context, List<String> failures) {
        DiagnosticSection section = new DiagnosticSection();
        long start = System.nanoTime();
        Future<?> future;
        try {
            future = pool.submit(() -> DiagnosticsGuard.run(() -> {
                try {
                    target.provider().contribute(context, section);
                } catch (Exception e) {
                    throw new ProviderFailure(e);
                }
            }));
        } catch (RejectedExecutionException e) {
            failures.add(target.key() + ": rejected (provider threads busy)");
            return status("rejected", 0, "provider threads busy");
        }
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            failures.add(target.key() + ": timed out after " + timeoutMs + " ms");
            return status("timed_out", elapsed(start), "no result within " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failures.add(target.key() + ": interrupted");
            return status("interrupted", elapsed(start), "interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() instanceof ProviderFailure pf ? pf.getCause() : e.getCause();
            String reason = cause == null ? "unknown error" : cause.getClass().getName() + ": " + cause.getMessage();
            failures.add(target.key() + ": " + reason);
            return status("failed", elapsed(start), reason);
        }
        JsonObject out = status("ok", elapsed(start), null);
        JsonObject entries = new JsonObject();
        for (Map.Entry<String, String> e : section.entries().entrySet()) {
            entries.addProperty(e.getKey(), e.getValue());
        }
        out.add("entries", entries);
        out.addProperty("truncated", section.truncated());
        return out;
    }

    private static JsonObject status(String status, long durationMs, String error) {
        JsonObject o = new JsonObject();
        o.addProperty("status", status);
        o.addProperty("durationMs", durationMs);
        if (error != null) {
            o.addProperty("error", error);
        }
        return o;
    }

    private static long elapsed(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private static final class ProviderFailure extends RuntimeException {
        ProviderFailure(Exception cause) {
            super(cause);
        }
    }
}
