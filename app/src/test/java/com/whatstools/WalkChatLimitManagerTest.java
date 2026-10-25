package com.whatstools;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.whatstools.walkChat.WalkChatLimitManager;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class WalkChatLimitManagerTest {
    private SharedPreferences sharedPreferences;

    @Before
    public void setUp() {
        // Mock SharedPreferences for testing
        sharedPreferences = new MockSharedPreferences();
    }

    @Test
    public void testSessionStartStoresCurrentTime() {
        long beforeStart = System.currentTimeMillis();
        WalkChatLimitManager.startSession(sharedPreferences);
        long afterStart = System.currentTimeMillis();

        long storedTime = sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L);
        assertTrue("Session start time should be stored", storedTime >= beforeStart && storedTime <= afterStart);
    }

    @Test
    public void testSessionEndClearsStartTime() {
        WalkChatLimitManager.startSession(sharedPreferences);
        assertTrue("Session should be active after start", WalkChatLimitManager.isSessionActive(sharedPreferences));

        WalkChatLimitManager.endSession(sharedPreferences);
        assertFalse("Session should be inactive after end", WalkChatLimitManager.isSessionActive(sharedPreferences));
    }

    @Test
    public void testIsSessionActiveReturnsFalseWhenNoSession() {
        assertFalse("Session should be inactive initially", WalkChatLimitManager.isSessionActive(sharedPreferences));
    }

    @Test
    public void testIsSessionActiveTrueWhenSessionStarted() {
        WalkChatLimitManager.startSession(sharedPreferences);
        assertTrue("Session should be active after start", WalkChatLimitManager.isSessionActive(sharedPreferences));
    }

    @Test
    public void testGetSessionElapsedMillisReturnsZeroWhenNoSession() {
        long elapsed = WalkChatLimitManager.getSessionElapsedMillis(sharedPreferences);
        assertEquals("Elapsed time should be 0 when no session", 0L, elapsed);
    }

    @Test
    public void testGetSessionElapsedMillisReturnsPositiveValue() {
        WalkChatLimitManager.startSession(sharedPreferences);
        // Wait a small amount to ensure elapsed time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long elapsed = WalkChatLimitManager.getSessionElapsedMillis(sharedPreferences);
        assertTrue("Elapsed time should be greater than 0", elapsed >= 0);
    }

    @Test
    public void testGetRemainingTimeMillisReturnsFullLimitAtStart() {
        WalkChatLimitManager.startSession(sharedPreferences);
        long remaining = WalkChatLimitManager.getRemainingTimeMillis(sharedPreferences);
        assertTrue("Remaining time should be close to limit at start",
                remaining >= WalkChatLimitManager.SESSION_LIMIT_MILLIS - 100 &&
                remaining <= WalkChatLimitManager.SESSION_LIMIT_MILLIS);
    }

    @Test
    public void testGetRemainingTimeMillisReturnsZeroWhenNoSession() {
        long remaining = WalkChatLimitManager.getRemainingTimeMillis(sharedPreferences);
        assertEquals("Remaining time should be 0 when no session", 0L, remaining);
    }

    @Test
    public void testIsSessionLimitReachedReturnsFalseAtStart() {
        WalkChatLimitManager.startSession(sharedPreferences);
        assertFalse("Limit should not be reached at start", WalkChatLimitManager.isSessionLimitReached(sharedPreferences));
    }

    @Test
    public void testIsSessionLimitReachedReturnsTrueWhenTimeExpired() {
        // Set session start to 16 minutes ago
        long sixteenMinutesAgo = System.currentTimeMillis() - (16 * 60 * 1000);
        sharedPreferences.edit().putLong(WalkChatLimitManager.KEY_SESSION_START, sixteenMinutesAgo).apply();

        assertTrue("Limit should be reached when time expired", WalkChatLimitManager.isSessionLimitReached(sharedPreferences));
    }

    @Test
    public void testGetRemainingTimeMillisDecreasesOverTime() {
        WalkChatLimitManager.startSession(sharedPreferences);
        long remaining1 = WalkChatLimitManager.getRemainingTimeMillis(sharedPreferences);

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long remaining2 = WalkChatLimitManager.getRemainingTimeMillis(sharedPreferences);
        assertTrue("Remaining time should decrease over time", remaining2 < remaining1);
    }

    @Test
    public void testSessionLimitIs15Minutes() {
        assertEquals("Session limit should be 15 minutes", 15 * 60 * 1000, WalkChatLimitManager.SESSION_LIMIT_MILLIS);
    }

    // Mock implementation of SharedPreferences for testing
    private static class MockSharedPreferences implements SharedPreferences {
        private final java.util.Map<String, Object> data = new java.util.HashMap<>();

        @Override
        public java.util.Map<String, ?> getAll() {
            return new java.util.HashMap<>(data);
        }

        @Override
        public String getString(String key, String defValue) {
            return (String) data.getOrDefault(key, defValue);
        }

        @Override
        public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
            return (java.util.Set<String>) data.getOrDefault(key, defValues);
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = data.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
            return defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = data.get(key);
            if (value instanceof Long) {
                return (Long) value;
            }
            return defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = data.get(key);
            if (value instanceof Float) {
                return (Float) value;
            }
            return defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = data.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return defValue;
        }

        @Override
        public boolean contains(String key) {
            return data.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MockEditor(data);
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        private static class MockEditor implements Editor {
            private final java.util.Map<String, Object> data;

            MockEditor(java.util.Map<String, Object> data) {
                this.data = data;
            }

            @Override
            public Editor putString(String key, String value) {
                data.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, java.util.Set<String> values) {
                data.put(key, values);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                data.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                data.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                data.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                data.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                data.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                data.clear();
                return this;
            }

            @Override
            public boolean commit() {
                return true;
            }

            @Override
            public void apply() {
                // No-op for mock
            }
        }
    }
}
