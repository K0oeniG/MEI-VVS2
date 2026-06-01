package com.example.meetings.discover;

import java.time.Instant;

/**
 * Provider-agnostic shape used by the discovery UI. {@code externalId} is provider-scoped
 * (so two different providers can share an id); {@code source + externalId} together uniquely
 * identify the event for dedup and for the "copy to calendar" form.
 */
public record DiscoveredEvent(
        String source,
        String externalId,
        String title,
        String description,
        Instant start,
        Instant end,
        String url,
        String venue) {
}
