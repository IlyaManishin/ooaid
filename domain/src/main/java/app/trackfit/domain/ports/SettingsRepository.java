package app.trackfit.domain.ports;

public interface SettingsRepository {
    boolean pushEnabled();
    void setPushEnabled(boolean enabled);
    String selectedCoachId();
    void setSelectedCoachId(String id);
}
