package com.vignanlive.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoleCacheService {

    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private final ConcurrentHashMap<String, CachedRole> roleCache = new ConcurrentHashMap<>();

    public CachedRole get(String email) {
        CachedRole cached = roleCache.get(email);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        if (cached != null) {
            roleCache.remove(email);
        }
        return null;
    }

    public void put(String email, String role, Map<String, Object> profile) {
        roleCache.put(email, new CachedRole(role, profile, System.currentTimeMillis()));
    }

    public void invalidate(String email) {
        roleCache.remove(email.toLowerCase());
    }

    public void clear() {
        roleCache.clear();
    }

    public record CachedRole(String role, Map<String, Object> profile, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }
}
