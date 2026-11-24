package app.trackfit.domain.model;

import java.time.LocalDate;
import java.time.Period;

public record Profile(String name, Sex sex, LocalDate birthday, String avatar) {
    public enum Sex { UNSPECIFIED, MALE, FEMALE }

    public static Profile empty() {
        return new Profile("", Sex.UNSPECIFIED, null, null);
    }

    public Integer ageOn(LocalDate date) {
        return birthday == null || birthday.isAfter(date) ? null : Period.between(birthday, date).getYears();
    }
}
