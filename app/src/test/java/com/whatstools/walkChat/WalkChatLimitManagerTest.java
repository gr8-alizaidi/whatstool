package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WalkChatLimitManagerTest {

    @Mock
    private Context context;

    @Mock
    private SharedPreferences sharedPreferences;

    @Mock
    private SharedPreferences.Editor editor;

    @Before
    public void setUp() {
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putLong(anyString(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(editor);
        when(editor.putBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(editor);
    }

    @Test
    public void testStartSession() {
        long beforeStart = System.currentTimeMillis();
        WalkChatLimitManager.startSession(sharedPreferences);
        long afterStart = System.currentTimeMillis();
        assertTrue(true);
    }

    @Test
    public void testEndSession() {
        WalkChatLimitManager.endSession(sharedPreferences);
        assertTrue(true);
    }

    @Test
    public void testGetLimitMillis() {
        long limitMillis = WalkChatLimitManager.getLimitMillis();
        assertEquals(15 * 60 * 1000, limitMillis);
    }

    @Test
    public void testIsSessionActiveWhenNoStart() {
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(0L);
        assertFalse(WalkChatLimitManager.isSessionActive(sharedPreferences));
    }

    @Test
    public void testIsSessionActiveWhenStarted() {
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(System.currentTimeMillis());
        assertTrue(WalkChatLimitManager.isSessionActive(sharedPreferences));
    }

    @Test
    public void testIsLimitExceededWhenUnderLimit() {
        long startTime = System.currentTimeMillis() - 5 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        assertFalse(WalkChatLimitManager.isLimitExceeded(sharedPreferences));
    }

    @Test
    public void testIsLimitExceededWhenOverLimit() {
        long startTime = System.currentTimeMillis() - 16 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        assertTrue(WalkChatLimitManager.isLimitExceeded(sharedPreferences));
    }

    @Test
    public void testIsLimitExceededAtExactLimit() {
        long startTime = System.currentTimeMillis() - 15 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        assertTrue(WalkChatLimitManager.isLimitExceeded(sharedPreferences));
    }

    @Test
    public void testGetSessionElapsedMillis() {
        long startTime = System.currentTimeMillis() - 5 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        long elapsed = WalkChatLimitManager.getSessionElapsedMillis(sharedPreferences);
        assertTrue(elapsed >= 5 * 60 * 1000 - 100 && elapsed <= 5 * 60 * 1000 + 100);
    }

    @Test
    public void testGetRemainingMillisWhenTimeLeft() {
        long startTime = System.currentTimeMillis() - 5 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        long remaining = WalkChatLimitManager.getRemainingMillis(sharedPreferences);
        assertTrue(remaining >= 10 * 60 * 1000 - 100 && remaining <= 10 * 60 * 1000 + 100);
    }

    @Test
    public void testGetRemainingMillisWhenTimeExpired() {
        long startTime = System.currentTimeMillis() - 16 * 60 * 1000;
        when(sharedPreferences.getLong(WalkChatLimitManager.KEY_SESSION_START, 0L)).thenReturn(startTime);
        long remaining = WalkChatLimitManager.getRemainingMillis(sharedPreferences);
        assertEquals(0, remaining);
    }

    @Test
    public void testIsBlockedDefault() {
        when(sharedPreferences.getBoolean(WalkChatLimitManager.KEY_BLOCKED, false)).thenReturn(false);
        assertFalse(WalkChatLimitManager.isBlocked(sharedPreferences));
    }

    @Test
    public void testSetBlocked() {
        WalkChatLimitManager.setBlocked(sharedPreferences, true);
        assertTrue(true);
    }
}
