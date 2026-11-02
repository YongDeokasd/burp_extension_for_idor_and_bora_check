package com.idorbola.checker.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.idorbola.checker.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanEngine {

    public interface ScanListener {
        void onResult(ScanResult result);
        void onProgress(int done, int total);
        void onComplete();
        void onError(String message);
    }

    private final MontoyaApi api;
    private final ScanConfig config;
    private final ScanListener listener;

    private final RequestDeduplicator deduplicator = new RequestDeduplicator();
    private final ProfileReplacer replacer = new ProfileReplacer();
    private final ResponseComparator comparator = new ResponseComparator();

    private final AtomicBoolean cancelled  = new AtomicBoolean(false);
    private final AtomicBoolean paused     = new AtomicBoolean(false);
    private ExecutorService executor;

    public ScanEngine(MontoyaApi api, ScanConfig config, ScanListener listener) {
        this.api      = api;
        this.config   = config;
        this.listener = listener;
    }

    public void startScan(List<HttpRequest> requests) {
        cancelled.set(false);
        paused.set(false);
        executor = Executors.newFixedThreadPool(Math.max(1, config.getThreadPoolSize()));

        // Coordinator must not run inside the worker pool: it blocks on the workers' futures.
        Thread coordinator = new Thread(() -> runScan(requests), "idor-bola-scan-coordinator");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    private void runScan(List<HttpRequest> requests) {
        try {
            // 1. Filter static resources and excluded patterns
            List<HttpRequest> filtered = new ArrayList<>();
            for (HttpRequest req : requests) {
                String path = req.path();
                int qIdx = path.indexOf('?');
                String pathOnly = qIdx >= 0 ? path.substring(0, qIdx) : path;
                if (config.isStaticResource(pathOnly)) continue;
                if (config.isExcluded(req.method(), pathOnly)) continue;
                filtered.add(req);
            }

            // 2. Deduplicate (or use all)
            List<RequestGroup> groups;
            if (config.isDeduplicationEnabled()) {
                groups = deduplicator.deduplicate(filtered);
            } else {
                groups = new ArrayList<>();
                for (HttpRequest req : filtered) {
                    groups.add(new RequestGroup(req, RequestDeduplicator.fingerprint(req)));
                }
            }

            int total = groups.size();
            AtomicInteger done = new AtomicInteger(0);

            // Rate limiter: tokens per second
            RateLimiter rateLimiter = new RateLimiter(config.getRateLimitPerSecond());

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < groups.size(); i++) {
                if (cancelled.get()) break;
                final RequestGroup group = groups.get(i);
                final int rowIdx = i;

                Future<?> f = executor.submit(() -> {
                    try {
                        processGroup(group, rowIdx, rateLimiter);
                    } catch (Exception e) {
                        listener.onError("Error processing " + group.getRepresentative().path() + ": " + e.getMessage());
                    } finally {
                        int d = done.incrementAndGet();
                        listener.onProgress(d, total);
                    }
                });
                futures.add(f);
            }

            // Wait for all
            for (Future<?> f : futures) {
                if (cancelled.get()) { f.cancel(false); continue; }
                try { f.get(); } catch (Exception ignored) {}
            }

            if (!cancelled.get()) listener.onComplete();

        } catch (Exception e) {
            listener.onError("Scan failed: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private void processGroup(RequestGroup group, int rowIdx, RateLimiter rateLimiter) throws InterruptedException {
        // Wait while paused
        while (paused.get() && !cancelled.get()) {
            Thread.sleep(200);
        }
        if (cancelled.get()) return;

        ScanResult result = new ScanResult(rowIdx, group);
        List<UserProfile> profiles = config.getProfiles();

        // Send request for each profile
        for (UserProfile profile : profiles) {
            if (cancelled.get()) return;

            rateLimiter.acquire();

            HttpRequest modified = replacer.apply(group.getRepresentative(), profile);
            try {
                HttpResponse response = api.http().sendRequest(modified).response();
                result.putResponse(profile.getId(), response);
            } catch (Exception e) {
                // Store null response — status will be -1
                api.logging().logToError("Error sending request for profile " + profile.getLabel() + ": " + e.getMessage());
            }

            // Inter-profile delay
            if (config.getProfileDelayMs() > 0) {
                Thread.sleep(config.getProfileDelayMs());
            }
        }

        // Compare all pairs
        for (int i = 0; i < profiles.size(); i++) {
            for (int j = i + 1; j < profiles.size(); j++) {
                UserProfile p1 = profiles.get(i);
                UserProfile p2 = profiles.get(j);
                HttpResponse r1 = result.getResponses().get(p1.getId());
                HttpResponse r2 = result.getResponses().get(p2.getId());
                if (r1 == null || r2 == null) continue;

                String body1 = r1.bodyToString();
                String body2 = r2.bodyToString();
                String ct    = r1.headerValue("Content-Type");

                float sim = comparator.compare(body1, body2, ct);
                result.putSimilarity(p1.getId(), p2.getId(), sim);
            }
        }

        listener.onResult(result);
    }

    public void pause()  { paused.set(true); }
    public void resume() { paused.set(false); }
    public void cancel() {
        cancelled.set(true);
        paused.set(false);
        if (executor != null) executor.shutdownNow();
    }

    // ── Simple token-bucket rate limiter ────────────────────────────────────

    private static class RateLimiter {
        private final int ratePerSecond;
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        RateLimiter(int ratePerSecond) {
            this.ratePerSecond = ratePerSecond;
        }

        synchronized void acquire() throws InterruptedException {
            long now = System.currentTimeMillis();
            if (now - windowStart >= 1000) {
                windowStart = now;
                count = 0;
            }
            if (count >= ratePerSecond) {
                long sleep = 1000 - (now - windowStart);
                if (sleep > 0) Thread.sleep(sleep);
                windowStart = System.currentTimeMillis();
                count = 0;
            }
            count++;
        }
    }
}
