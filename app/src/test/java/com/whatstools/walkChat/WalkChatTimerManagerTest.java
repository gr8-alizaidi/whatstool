package com.whatstools.walkChat;

import org.junit.Test;

import static org.junit.Assert.*;

public class WalkChatTimerManagerTest {

    @Test
    public void testTimerConstantsAreDefined() {
        assertEquals(15 * 60 * 1000, WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS);
        assertNotNull(WalkChatTimerManager.KEY_WALK_CHAT_TIMER_START);
    }

    @Test
    public void testIsTimerExpiredAfter15MinutesLogic() {
        long startTime = System.currentTimeMillis() - (15 * 60 * 1000);
        long elapsedTime = System.currentTimeMillis() - startTime;
        boolean isExpired = elapsedTime >= WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS;
        assertTrue(isExpired);
    }

    @Test
    public void testIsTimerNotExpiredBelow15MinutesLogic() {
        long startTime = System.currentTimeMillis() - (10 * 60 * 1000);
        long elapsedTime = System.currentTimeMillis() - startTime;
        boolean isExpired = elapsedTime >= WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS;
        assertFalse(isExpired);
    }

    @Test
    public void testRemainingTimeCalculationLogic() {
        long startTime = System.currentTimeMillis() - (5 * 60 * 1000);
        long elapsedTime = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS - elapsedTime);
        assertTrue(remaining <= WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS);
        assertTrue(remaining > 0);
    }

    @Test
    public void testRemainingTimeAfter15MinutesLogic() {
        long startTime = System.currentTimeMillis() - (15 * 60 * 1000);
        long elapsedTime = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, WalkChatTimerManager.WALK_CHAT_DURATION_MILLIS - elapsedTime);
        assertEquals(0, remaining);
    }
}
