package app.trackfit.app.presentation;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import app.trackfit.domain.coach.CoachDefinition;
import app.trackfit.domain.controller.CoachController;
import app.trackfit.domain.controller.Observation;
import app.trackfit.domain.ports.TimeSource;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;

@HiltViewModel
public final class CoachViewModel extends ViewModel {
    public record Card(String id, String name, String character, String bio, String preview, String avatar,
                       boolean selected) {}
    public record State(List<Card> cards, Card selected, String line, boolean resultLine) {}

    private final CoachController coach;
    private final TimeSource time;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<State> state = new MutableLiveData<>(
            new State(Collections.emptyList(), null, null, false));
    private final Observation.Subscription subscription;
    private final Runnable tick;
    private final Runnable expire;
    private CoachController.State source;
    private boolean homeVisible;
    private boolean cleared;

    @Inject
    public CoachViewModel(CoachController coach, TimeSource time) {
        this.coach = coach;
        this.time = time;
        tick = coach::tick;
        expire = coach::dismiss;
        subscription = coach.state().subscribe(data -> main.post(() -> {
            if (cleared) return;
            source = data;
            update();
        }));
    }

    public LiveData<State> state() { return state; }

    public void homeVisible(boolean visible) {
        homeVisible = visible;
        schedule();
    }

    public void reportPresented(UUID id) { coach.reportPresented(id); }
    public void select(String id) { coach.select(id); }
    public void dismiss() { coach.dismiss(); }

    private void update() {
        List<Card> cards = new ArrayList<>();
        Card selected = null;
        for (var item : source.coaches()) {
            Card card = map(item, item.equals(source.selected()));
            cards.add(card);
            if (card.selected()) selected = card;
        }
        String line = source.line() == null || !source.line().expiresAt().isAfter(time.now())
                ? null : source.line().text();
        state.setValue(new State(cards, selected, line, line != null
                && source.line().priority() == app.trackfit.domain.coach.CoachRule.Priority.RESULT));
        schedule();
    }

    private void schedule() {
        main.removeCallbacks(tick);
        main.removeCallbacks(expire);
        if (source == null) return;
        var now = time.now();
        if (source.line() != null) {
            main.postDelayed(expire, Math.max(1, Duration.between(now, source.line().expiresAt()).toMillis()));
        }
        if (homeVisible && source.nextMotivationAt() != null) {
            main.postDelayed(tick, Math.max(1, Duration.between(now, source.nextMotivationAt()).toMillis()));
        }
    }

    private static Card map(CoachDefinition definition, boolean selected) {
        return new Card(definition.id(), definition.displayName(), definition.character(), definition.bio(),
                definition.preview(), definition.avatar(), selected);
    }

    @Override
    protected void onCleared() {
        cleared = true;
        subscription.close();
        main.removeCallbacksAndMessages(null);
    }
}
