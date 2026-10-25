package com.whatstools.walkChat;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class WalkChatSessionManagerTest {
    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockSharedPreferences;

    @Mock
    private SharedPreferences.Editor mockEditor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);
    }

    @Test
    public void testSessionStartRecordsStartTime() {
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);

        long beforeStart = System.currentTimeMillis();
        WalkChatSessionManager.startSession(mockContext, null);
        long afterStart = System.currentTimeMillis();

        verify(mockEditor).putLong(eq("walk_chat_session_start"), anyLong());
        verify(mockEditor).apply();
    }

    @Test
    public void testSessionStopClearsStartTime() {
        when(mockSharedPreferences.edit()).thenReturn(mockEditor);

        WalkChatSessionManager.stopSession(mockContext);

        verify(mockEditor).remove("walk_chat_session_start");
        verify(mockEditor).apply();
    }

    @Test
    public void testIsSessionActiveReturnsTrueWhenActive() {
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(System.currentTimeMillis());

        assertTrue(WalkChatSessionManager.isSessionActive(mockContext));
    }

    @Test
    public void testIsSessionActiveReturnsFalseWhenInactive() {
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(0);

        assertFalse(WalkChatSessionManager.isSessionActive(mockContext));
    }

    @Test
    public void testGetSessionElapsedMillisReturnsZeroWhenInactive() {
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(0);

        assertEquals(0, WalkChatSessionManager.getSessionElapsedMillis(mockContext));
    }

    @Test
    public void testGetSessionElapsedMillisReturnsElapsedTime() {
        long sessionStart = System.currentTimeMillis() - 5000;
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(sessionStart);

        long elapsed = WalkChatSessionManager.getSessionElapsedMillis(mockContext);
        assertTrue(elapsed >= 5000);
        assertTrue(elapsed < 6000);
    }

    @Test
    public void testHasSessionExpiredReturnsFalseWhenNotExpired() {
        long sessionStart = System.currentTimeMillis() - (14 * 60 * 1000);
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(sessionStart);

        assertFalse(WalkChatSessionManager.hasSessionExpired(mockContext));
    }

    @Test
    public void testHasSessionExpiredReturnsTrueWhenExpired() {
        long sessionStart = System.currentTimeMillis() - (16 * 60 * 1000);
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(sessionStart);

        assertTrue(WalkChatSessionManager.hasSessionExpired(mockContext));
    }

    @Test
    public void testGetRemainingSessionMillisReturnsRemainingTime() {
        long sessionStart = System.currentTimeMillis() - (5 * 60 * 1000);
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(sessionStart);

        long remaining = WalkChatSessionManager.getRemainingSessionMillis(mockContext);
        assertTrue(remaining > (9 * 60 * 1000));
        assertTrue(remaining <= (10 * 60 * 1000));
    }

    @Test
    public void testGetRemainingSessionMillisReturnsZeroWhenExpired() {
        long sessionStart = System.currentTimeMillis() - (16 * 60 * 1000);
        when(mockSharedPreferences.getLong("walk_chat_session_start", 0))
            .thenReturn(sessionStart);

        assertEquals(0, WalkChatSessionManager.getRemainingSessionMillis(mockContext));
    }

    @Test
    public void testSessionTimeoutConstant() {
        long timeout = WalkChatSessionManager.getSessionTimeoutMillis();
        assertEquals(15 * 60 * 1000, timeout);
    }
}
