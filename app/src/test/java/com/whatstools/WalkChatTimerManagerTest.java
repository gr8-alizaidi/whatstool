package com.whatstools;

import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.whatstools.walkChat.WalkChatTimerManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WalkChatTimerManagerTest {
    @Mock
    private SharedPreferences mockPrefs;

    @Mock
    private SharedPreferences.Editor mockEditor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
    }

    @Test
    public void startSession_SetsActiveToTrue() {
        WalkChatTimerManager.startSession(mockPrefs);
        verify(mockEditor).putBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, true);
        verify(mockEditor).apply();
    }

    @Test
    public void stopSession_SetsActiveToFalse() {
        WalkChatTimerManager.stopSession(mockPrefs);
        verify(mockEditor).putBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, false);
        verify(mockEditor).apply();
    }

    @Test
    public void isWalkChatActive_ReturnsFalseByDefault() {
        when(mockPrefs.getBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, false)).thenReturn(false);
        assertFalse(WalkChatTimerManager.isWalkChatActive(mockPrefs));
    }

    @Test
    public void isWalkChatActive_ReturnsTrueWhenActive() {
        when(mockPrefs.getBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, false)).thenReturn(true);
        assertTrue(WalkChatTimerManager.isWalkChatActive(mockPrefs));
    }

    @Test
    public void getSessionStartTime_ReturnsZeroByDefault() {
        when(mockPrefs.getLong(WalkChatTimerManager.KEY_WALK_SESSION_START, 0L)).thenReturn(0L);
        assertEquals(0L, WalkChatTimerManager.getSessionStartTime(mockPrefs));
    }

    @Test
    public void getElapsedMillis_ReturnsZeroWhenNotStarted() {
        when(mockPrefs.getLong(WalkChatTimerManager.KEY_WALK_SESSION_START, 0L)).thenReturn(0L);
        assertEquals(0L, WalkChatTimerManager.getElapsedMillis(mockPrefs));
    }

    @Test
    public void hasExceededLimit_ReturnsFalseWhenNotActive() {
        when(mockPrefs.getBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, false)).thenReturn(false);
        assertFalse(WalkChatTimerManager.hasExceededLimit(mockPrefs));
    }

    @Test
    public void getRemainingMillis_ReturnsLimitWhenJustStarted() {
        when(mockPrefs.getBoolean(WalkChatTimerManager.KEY_WALK_ENABLED, false)).thenReturn(true);
        long now = System.currentTimeMillis();
        when(mockPrefs.getLong(WalkChatTimerManager.KEY_WALK_SESSION_START, 0L)).thenReturn(now);
        long remaining = WalkChatTimerManager.getRemainingMillis(mockPrefs);
        assertTrue(remaining > 0 && remaining <= 15 * 60 * 1000);
    }
}
