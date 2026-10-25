package com.whatstools.birthday;

import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReminderSchedulerTest {
    @Mock
    private SharedPreferences mockPrefs;

    private List<BirthdayModel> testBirthdays;

    @Before
    public void setUp() {
        testBirthdays = new ArrayList<>();
        testBirthdays.add(new BirthdayModel("John Doe", "+1234567890", 7, 15, 1990));
        testBirthdays.add(new BirthdayModel("Jane Smith", "+0987654321", 12, 25));
    }

    @Test
    public void testIsEnabledReturnsFalseByDefault() {
        when(mockPrefs.getBoolean(ReminderScheduler.KEY_ENABLED, false)).thenReturn(false);
        assertFalse(ReminderScheduler.isEnabled(mockPrefs));
    }

    @Test
    public void testGetDaysBeforeReturnsDefaultValue() {
        when(mockPrefs.getInt(ReminderScheduler.KEY_DAYS_BEFORE, 1)).thenReturn(1);
        assertEquals(1, ReminderScheduler.getDaysBefore(mockPrefs));
    }

    @Test
    public void testGetDaysBeforeReturnsCustomValue() {
        when(mockPrefs.getInt(ReminderScheduler.KEY_DAYS_BEFORE, 1)).thenReturn(3);
        assertEquals(3, ReminderScheduler.getDaysBefore(mockPrefs));
    }

    @Test
    public void testSetDaysBeforeWithValidValue() {
        when(mockPrefs.edit()).thenReturn(mockPrefs);
        when(mockPrefs.putInt(ReminderScheduler.KEY_DAYS_BEFORE, 5)).thenReturn(mockPrefs);
        ReminderScheduler.setDaysBefore(mockPrefs, 5);
        assertTrue(true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetDaysBeforeWithNegativeValue() {
        ReminderScheduler.setDaysBefore(mockPrefs, -1);
    }

    @Test
    public void testGetNotificationStyleReturnsSingleByDefault() {
        when(mockPrefs.getString(ReminderScheduler.KEY_NOTIFICATION_STYLE, ReminderScheduler.NOTIFICATION_STYLE_SINGLE))
                .thenReturn(ReminderScheduler.NOTIFICATION_STYLE_SINGLE);
        assertEquals(ReminderScheduler.NOTIFICATION_STYLE_SINGLE, ReminderScheduler.getNotificationStyle(mockPrefs));
    }

    @Test
    public void testGetNotificationStyleReturnsDual() {
        when(mockPrefs.getString(ReminderScheduler.KEY_NOTIFICATION_STYLE, ReminderScheduler.NOTIFICATION_STYLE_SINGLE))
                .thenReturn(ReminderScheduler.NOTIFICATION_STYLE_DUAL);
        assertEquals(ReminderScheduler.NOTIFICATION_STYLE_DUAL, ReminderScheduler.getNotificationStyle(mockPrefs));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetNotificationStyleWithInvalidStyle() {
        ReminderScheduler.setNotificationStyle(mockPrefs, "invalid");
    }

    @Test
    public void testGetReminderEventsWhenDisabled() {
        when(mockPrefs.getBoolean(ReminderScheduler.KEY_ENABLED, false)).thenReturn(false);
        List<ReminderScheduler.ReminderEvent> events = ReminderScheduler.getReminderEvents(testBirthdays, mockPrefs);
        assertEquals(0, events.size());
    }

    @Test
    public void testGetReminderEventsWithSingleNotificationStyle() {
        when(mockPrefs.getBoolean(ReminderScheduler.KEY_ENABLED, false)).thenReturn(true);
        when(mockPrefs.getInt(ReminderScheduler.KEY_DAYS_BEFORE, 1)).thenReturn(1);
        when(mockPrefs.getString(ReminderScheduler.KEY_NOTIFICATION_STYLE, ReminderScheduler.NOTIFICATION_STYLE_SINGLE))
                .thenReturn(ReminderScheduler.NOTIFICATION_STYLE_SINGLE);

        List<ReminderScheduler.ReminderEvent> events = ReminderScheduler.getReminderEvents(testBirthdays, mockPrefs);

        assertTrue(events.size() > 0);
        boolean hasJohnEvent = false;
        for (ReminderScheduler.ReminderEvent event : events) {
            if ("John Doe".equals(event.getBirthday().getContactName())) {
                hasJohnEvent = true;
                break;
            }
        }
        assertTrue("Should contain John's birthday event", hasJohnEvent);
    }

    @Test
    public void testGetReminderEventsWithDualNotificationStyle() {
        when(mockPrefs.getBoolean(ReminderScheduler.KEY_ENABLED, false)).thenReturn(true);
        when(mockPrefs.getInt(ReminderScheduler.KEY_DAYS_BEFORE, 1)).thenReturn(4);
        when(mockPrefs.getString(ReminderScheduler.KEY_NOTIFICATION_STYLE, ReminderScheduler.NOTIFICATION_STYLE_SINGLE))
                .thenReturn(ReminderScheduler.NOTIFICATION_STYLE_DUAL);

        List<ReminderScheduler.ReminderEvent> events = ReminderScheduler.getReminderEvents(testBirthdays, mockPrefs);

        assertTrue("Should have events with dual notifications", events.size() > 0);
        int johnEventCount = 0;
        for (ReminderScheduler.ReminderEvent event : events) {
            if ("John Doe".equals(event.getBirthday().getContactName())) {
                johnEventCount++;
            }
        }
        assertTrue("Should have multiple events for John with dual style", johnEventCount >= 2);
    }

    @Test
    public void testReminderEventTypeLabels() {
        Calendar now = Calendar.getInstance();
        BirthdayModel birthday = new BirthdayModel("Test", "+123", 1, 1);

        ReminderScheduler.ReminderEvent primaryEvent = new ReminderScheduler.ReminderEvent(birthday, now, ReminderScheduler.ReminderEvent.TYPE_PRIMARY);
        assertEquals("primary", primaryEvent.getTypeLabel());

        ReminderScheduler.ReminderEvent secondaryEvent = new ReminderScheduler.ReminderEvent(birthday, now, ReminderScheduler.ReminderEvent.TYPE_SECONDARY);
        assertEquals("secondary", secondaryEvent.getTypeLabel());

        ReminderScheduler.ReminderEvent birthdayEvent = new ReminderScheduler.ReminderEvent(birthday, now, ReminderScheduler.ReminderEvent.TYPE_BIRTHDAY);
        assertEquals("birthday", birthdayEvent.getTypeLabel());
    }

    @Test
    public void testBirthdayModelWithoutYear() {
        BirthdayModel birthday = new BirthdayModel("Test", "+123", 5, 10);
        assertFalse(birthday.hasYear());
    }

    @Test
    public void testBirthdayModelWithYear() {
        BirthdayModel birthday = new BirthdayModel("Test", "+123", 5, 10, 1995);
        assertTrue(birthday.hasYear());
    }
}
