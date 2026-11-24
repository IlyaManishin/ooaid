package app.trackfit.infra.prefs;

import android.content.SharedPreferences;
import app.trackfit.domain.ports.SettingsRepository;

public final class LocalSettings implements SettingsRepository {
    private static final String PUSH_ENABLED = "push_enabled";
    private static final String COACH_ID = "coach_id";
    private final SharedPreferences preferences;

    public LocalSettings(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @Override public boolean pushEnabled() { return preferences.getBoolean(PUSH_ENABLED, true); }
    @Override public String selectedCoachId() { return preferences.getString(COACH_ID, null); }

    @Override
    public void setPushEnabled(boolean enabled) {
        persist(preferences.edit().putBoolean(PUSH_ENABLED, enabled));
    }

    @Override
    public void setSelectedCoachId(String id) {
        persist(preferences.edit().putString(COACH_ID, id));
    }

    private static void persist(SharedPreferences.Editor edit) {
        if (!edit.commit()) {
            throw new IllegalStateException("Could not persist TrackFit settings");
        }
    }
}
