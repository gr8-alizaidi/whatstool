<!-- DECISION-FE011105 -->
## Decision: Create a base class for all Activity classes

**Status**: Active  
**Date**: 2026-07-13  
**Severity**: Critical

**Files**:
- `**/*`

### Context

**Decision:** Create a base class for activity and have all other activities extend this base class only.

---

<!-- DECISION-E67B7433 -->
## Decision: Development ownership for Activity base class refactoring

**Status**: Active  
**Date**: 2026-07-13  
**Severity**: Warning

**Files**:
- `**/*`

### Context

**Decision:** Jason and his team will be responsible for implementing the architectural change of creating and extending the Activity base class.

---

<!-- DECISION-EB14C947 -->
## Decision: Enforce WhatsApp screen limit with AccessibilityService and SharedPreferences

**Status**: Active  
**Date**: 2026-07-12  
**Severity**: Warning

**Files**:
- `app/src/main/java/com/whatstools/walkChat/BasicAccessibilityService.java`
- `app/src/main/java/com/whatstools/screenlimit/ScreenLimitManager.java`
- `app/src/main/java/com/whatstools/screenlimit/ScreenLimitSettingsActivity.java`
- `app/src/main/java/com/whatstools/screenlimit/WhatsAppLimitBlockActivity.java`
- `app/src/main/res/layout/activity_screen_limit_settings.xml`
- `app/src/main/res/layout/activity_screen_limit_block.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/AndroidManifest.xml`

### Context

**Decision:** Track WhatsApp foreground sessions in the existing AccessibilityService, accumulate daily usage in shared preferences, reset usage at midnight, and launch a blocking activity once the configured limit is reached. Expose configuration through an XML settings screen and keep the implementation non-Compose.

---

<!-- DECISION-B126FBD7 -->
## Decision: Testing ownership for post-revamp activity features

**Status**: Active  
**Date**: 2026-07-13  
**Severity**: Warning

**Files**:
- `**/*`

### Context

**Decision:** Ron and his team will be responsible for testing all features after the activity revamp is complete.

---

<!-- DECISION-01A27519 -->
## Decision: Manager assignment for the Activity revamp teams

**Status**: Active  
**Date**: 2026-07-13  
**Severity**: Info

**Rules**:
```json
{
  "conditions": [
    {
      "type": "file",
      "pattern": "**/*",
      "content_rules": [
        {
          "mode": "full_file"
        }
      ]
    }
  ],
  "match_mode": "all"
}
```

### Context

**Decision:** Andrew will serve as the common manager for both Jason's and Ron's teams during the revamp.
