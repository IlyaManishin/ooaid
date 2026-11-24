package app.trackfit.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.CoachViewModel;
import com.google.android.material.button.MaterialButton;

public final class CoachFragment extends Fragment {
    private static final String COACH = "coach";
    private CoachViewModel model;

    public CoachFragment() {
        super(R.layout.fragment_training);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        LinearLayout content = FragmentTrainingBinding.bind(view).trainingContent;
        model = new ViewModelProvider(requireActivity()).get(CoachViewModel.class);
        String selected = getArguments() == null ? null : getArguments().getString(COACH);
        model.state().observe(getViewLifecycleOwner(), state -> {
            content.removeAllViews();
            text(content, getString(R.string.coach_title), true);
            if (selected == null) {
                text(content, getString(R.string.coach_subtitle), false);
                for (var card : state.cards()) {
                    String label = card.selected() ? getString(R.string.coach_selected, card.name()) : card.name();
                    action(content, label, () -> {
                        Bundle args = new Bundle();
                        args.putString(COACH, card.id());
                        var nav = NavHostFragment.findNavController(this);
                        if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == R.id.coach_screen) {
                            nav.navigate(R.id.coach_card_screen, args);
                        }
                    });
                    text(content, card.character(), true);
                    text(content, card.preview(), false);
                }
            } else {
                for (var card : state.cards()) {
                    if (!card.id().equals(selected)) continue;
                    action(content, getString(R.string.nav_back), () -> NavHostFragment.findNavController(this).popBackStack());
                    LinearLayout bar = new LinearLayout(requireContext());
                    CoachBar.render(bar, new CoachViewModel.State(state.cards(), card, null, false), () -> {});
                    content.addView(bar);
                    text(content, getString(R.string.coach_character), true);
                    text(content, card.character(), false);
                    text(content, getString(R.string.coach_bio), true);
                    text(content, card.bio(), false);
                    action(content, getString(R.string.coach_choose), () -> {
                        model.select(card.id());
                        NavHostFragment.findNavController(this).popBackStack();
                    });
                }
            }
        });
    }

    private void text(LinearLayout parent, String content, boolean title) {
        TextView view = new TextView(requireContext());
        view.setText(content);
        view.setTextAppearance(title ? com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
                : com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        parent.addView(view);
    }

    private void action(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }
}
