package com.example.meetings.discover;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DiscoveryService {

    private final List<EventProvider> providers;

    public DiscoveryService(List<EventProvider> providers) {
        this.providers = providers;
    }

    public List<EventProvider> providers() { return providers; }

    /** Fans out to every configured provider and dedupes by URL. Results are sorted by start time. */
    public List<DiscoveredEvent> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        Set<String> seenUrls = new HashSet<>();
        List<DiscoveredEvent> merged = new ArrayList<>();
        for (EventProvider p : providers) {
            if (!p.isConfigured()) continue;
            for (DiscoveredEvent e : p.search(query)) {
                // URL is the most reliable cross-provider dedup key; fall back to source+id when missing.
                String key = e.url() != null ? e.url() : e.source() + ":" + e.externalId();
                if (seenUrls.add(key)) merged.add(e);
            }
        }
        merged.sort(Comparator.comparing(DiscoveredEvent::start));
        return merged;
    }
}
