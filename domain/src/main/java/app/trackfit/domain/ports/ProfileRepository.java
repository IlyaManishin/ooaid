package app.trackfit.domain.ports;

import app.trackfit.domain.model.Profile;

public interface ProfileRepository {
    Profile loadProfile();
    void saveProfile(Profile profile);
}
