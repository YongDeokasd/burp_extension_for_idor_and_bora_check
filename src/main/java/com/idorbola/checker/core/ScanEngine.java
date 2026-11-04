package com.idorbola.checker.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.idorbola.checker.model.*;
import com.idorbola.checker.util.HttpText;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanEngine {

    public interface ScanListener {
        void onResult(ScanResult result);
        void onProgress(int done, int total);
        void onComplete();
        void onError(String message);
        /** Scan plan: requests received, how many were filtered out, how many endpoints will be replayed. */
        default void onPlanned(int received, int excluded, int toScan) {}
        /** An endpoint already scanned earlier (same fingerprint); not replayed again. */
        default void onDuplicate(RequestGroup group) {}
    }

    /** Row numbering and already-scanned fingerprints shared by every scan appended to the same table. */
    public record ScanContext(AtomicInteger rowCounter, Set<String> scannedFingerprints) {}

    private final MontoyaApi api;
    private final ScanConfig config;
    private final ScanListener listener;
    private final ScanContext context;

    private final RequestDeduplicator deduplicator = new RequestDeduplicator();
    private final ProfileReplacer replacer = new ProfileReplacer();
    private final ResponseComparator comparator = new ResponseComparator();

    private final AtomicBoolean cancelled  = new AtomicBoolean(false);
    private final AtomicBoolean paused     = new AtomicBoolean(false);
    private RequestOptions requestOptions;
    private final Set<String> delivered    = ConcurrentHashMap.newKeySet();
    private ExecutorService executor;

    public ScanEngine(MontoyaApi api, ScanConfig config, ScanContext context, ScanListener listener) {
        this.api      = api;
        this.config   = config;
        this.context  = context;
        this.listener = listener;
    }

    public void startScan(List<HttpRequest> requests) {
        cancelled.set(false);
        paused.set(false);
        executor = Executors.newFixedThreadPool(Math.max(1, config.getThreadPoolSize()));
        requestOptions = timeoutOptions();

        // Coordinator must not run inside the worker pool: it blocks on the workers' futures.
        Thread coordinator = new Thread(() -> runScan(requests), "idor-bola-scan-coordinator");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    private void runScan(List<HttpRequest> requests) {
        try {
            // 1. Filter OPTIONS (CORS preflight, no authorization semantics), static resources, exclusions
            List<HttpRequest> filtered = new ArrayList<>();
            for (HttpRequest req : requests) {
                if ("OPTIONS".equalsIgnoreCase(req.method())) continue;
                String path = req.path();
                int qIdx = path.indexOf('?');
                String pathOnly = qIdx >= 0 ? path.substring(0, qIdx) : path;
                if (config.isStaticResource(pathOnly)) continue;
                if (config.isExcluded(req.method(), pathOnly)) continue;
                filtered.add(req);
            }

            // 2. Deduplicate within this batch and against endpoints already in the results table
            List<RequestGroup> groups = new ArrayList<>();
            if (config.isDeduplicationEnabled()) {
                for (RequestGroup g : deduplicator.deduplicate(filtered, config.getDedupLevel())) {
                    if (context.scannedFingerprints().add(g.getFingerprint())) {
                        groups.add(g);
                    } else {
                        listener.onDuplicate(g);
                    }
                }
            } else {
                for (HttpRequest req : filtered) {
                    groups.add(new RequestGroup(req, RequestDeduplicator.fingerprint(req, DedupLevel.OFF)));
                }
            }

            int total = groups.size();
            listener.onPlanned(requests.size(), requests.size() - filtered.size(), total);
            AtomicInteger done = new AtomicInteger(0);

            // Rate limiter: tokens per second
            RateLimiter rateLimiter = new RateLimiter(config.getRateLimitPerSecond());

            List<Future<?>> futures = new ArrayList<>();
            for (RequestGroup group : groups) {
                if (cancelled.get()) break;
                final int rowIdx = context.rowCounter().getAndIncrement();

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

            if (cancelled.get()) {
                // Endpoints never replayed must stay scannable by a later scan.
                if (config.isDeduplicationEnabled()) {
                    for (RequestGroup g : groups) {
                        if (!delivered.contains(g.getFingerprint())) context.scannedFingerprints().remove(g.getFingerprint());
                    }
                }
            } else {
                listener.onComplete();
            }

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
            result.putRequest(profile.getId(), modified);
            try {
                HttpRequestResponse sent = requestOptions != null
                        ? api.http().sendRequest(modified, requestOptions)
                        : api.http().sendRequest(modified);
                HttpResponse response = sent.response();
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

                String body1 = HttpText.body(r1);
                String body2 = HttpText.body(r2);
                String ct    = r1.headerValue("Content-Type");

                float sim = comparator.compare(body1, body2, ct);
                result.putSimilarity(p1.getId(), p2.getId(), sim);
            }
        }

        delivered.add(group.getFingerprint());
        listener.onResult(result);
    }

    /** Per-response timeout from the settings; null on Burp versions without RequestOptions timeouts. */
    private RequestOptions timeoutOptions() {
        try {
            return RequestOptions.requestOptions().withResponseTimeout(config.getTimeoutSeconds() * 1000L);
        } catch (LinkageError e) {
            api.logging().logToOutput("This Burp version does not support per-request timeouts; "
                    + "using Burp's default network timeout.");
            return null;
        }
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
