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
