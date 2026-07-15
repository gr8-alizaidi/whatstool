package com.whatstools.birthday;

public class BirthdayModel {
    private String contactName;
    private String phoneNumber;
    private int month;
    private int dayOfMonth;
    private int year;

    public BirthdayModel(String contactName, String phoneNumber, int month, int dayOfMonth) {
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
        this.month = month;
        this.dayOfMonth = dayOfMonth;
        this.year = -1;
    }

    public BirthdayModel(String contactName, String phoneNumber, int month, int dayOfMonth, int year) {
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
        this.month = month;
        this.dayOfMonth = dayOfMonth;
        this.year = year;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public void setDayOfMonth(int dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public boolean hasYear() {
        return year > 0;
    }
}
