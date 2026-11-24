package app.trackfit.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.CoachViewModel;
import app.trackfit.app.presentation.CoachViewModel.Card;

public final class CoachFragment extends Fragment {
    private static final String COACH = "coach";

    public CoachFragment() {
        super(R.layout.fragment_training);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        var binding = FragmentTrainingBinding.bind(view);
        var model = new ViewModelProvider(requireActivity()).get(CoachViewModel.class);
        String coachId = getArguments() == null ? null : getArguments().getString(COACH);
        model.state().observe(getViewLifecycleOwner(), state -> {
            LinearLayout content = binding.trainingContent;
            content.removeAllViews();
            binding.screenFooter.removeAllViews();
            if (coachId == null) {
                Ui.heading(content, getString(R.string.coach_title));
                Ui.block(content, Ui.text(requireContext(), getString(R.string.coach_subtitle),
                        14, R.color.ui_muted, false), 16);
                for (int index = 0; index < state.cards().size(); index++) {
                    addCoachCard(content, state.cards().get(index), index);
                }
            } else {
                for (int index = 0; index < state.cards().size(); index++) {
                    Card coach = state.cards().get(index);
                    if (!coach.id().equals(coachId)) continue;
                    showCoach(content, coach, index);
                    binding.screenFooter.setVisibility(View.VISIBLE);
                    Ui.block(binding.screenFooter, Ui.button(requireContext(), R.string.coach_choose, 0,
                            R.color.ui_text, R.color.ui_card, () -> {
                                model.select(coach.id());
                                NavHostFragment.findNavController(this).popBackStack();
                            }), 0);
                    break;
                }
            }
        });
    }

    private void addCoachCard(LinearLayout parent, Card coach, int index) {
        Context context = parent.getContext();
        LinearLayout card = Ui.column(context);
        Ui.pad(card, 14, 12);
        Ui.tap(card, R.color.ui_card, 18,
                coach.selected() ? R.color.ui_green_border : R.color.ui_border, () -> openCoach(coach.id()));
        Ui.block(card, listIdentity(coach, index), 10);
        addQuote(card, coach, index);
        Ui.block(parent, card, 10);
    }

    private void showCoach(LinearLayout parent, Card coach, int index) {
        Context context = parent.getContext();
        ScreenHeader.addTo(parent, getString(R.string.coach_title),
                () -> NavHostFragment.findNavController(this).popBackStack());
        LinearLayout profile = Ui.row(context);
        Ui.pad(profile, 14, 14);
        Ui.paper(profile, R.color.ui_card, 18, R.color.ui_border);
        profile.addView(CoachAvatar.create(context, coach, 122, ink(index), fill(index)));
        LinearLayout copy = Ui.column(context);
        Ui.pad(copy, 14, 0);
        TextView name = Ui.text(context, coach.name(), 23, R.color.ui_text, true);
        ViewCompat.setAccessibilityHeading(name, true);
        Ui.block(copy, name, 3);
        Ui.block(copy, Ui.text(context, getString(R.string.tab_coach), 15, R.color.ui_muted, false), 0);
        if (coach.selected()) addSelectedBadge(copy, coach);
        Ui.weight(profile, copy, 0);
        Ui.block(parent, profile, 10);

        addDetail(parent, R.string.coach_character, coach.character(), R.drawable.ic_ui_character,
                R.color.ui_violet, R.color.ui_lilac);
        addDetail(parent, R.string.coach_bio, coach.bio(), R.drawable.ic_ui_bio,
                R.color.ui_ocean, R.color.ui_blue);
    }

    private void addDetail(LinearLayout parent, int title, String description, int icon, int ink, int fill) {
        Context context = parent.getContext();
        LinearLayout card = Ui.row(context);
        card.setGravity(Gravity.TOP);
        Ui.pad(card, 14, 14);
        Ui.paper(card, R.color.ui_card, 16, R.color.ui_border);
        card.addView(Ui.badge(context, icon, ink, fill, 36));
        LinearLayout copy = Ui.column(context);
        Ui.pad(copy, 10, 0);
        TextView heading = Ui.text(context, getString(title), 16, R.color.ui_text, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        Ui.block(copy, heading, 4);
        Ui.block(copy, Ui.text(context, description, 14, R.color.ui_muted, false), 0);
        Ui.weight(card, copy, 0);
        Ui.block(parent, card, 10);
    }

    private LinearLayout listIdentity(Card coach, int index) {
        Context context = requireContext();
        LinearLayout row = Ui.row(context);
        row.addView(CoachAvatar.create(context, coach, 56, ink(index), fill(index)));
        LinearLayout copy = Ui.column(context);
        Ui.pad(copy, 10, 0);
        copy.addView(Ui.text(context, coach.name(), 19, R.color.ui_text, true));
        copy.addView(Ui.text(context, coach.character(), 14, ink(index), false));
        if (coach.selected()) addSelectedBadge(copy, coach);
        Ui.weight(row, copy, 0);
        row.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_muted, 21));
        return row;
    }

    private void addSelectedBadge(LinearLayout parent, Card coach) {
        Context context = parent.getContext();
        TextView badge = Ui.text(context, getString(R.string.coach_selected_badge), 12, R.color.ui_green, true);
        badge.setContentDescription(getString(R.string.coach_selected, coach.name()));
        Ui.pad(badge, 7, 2);
        Ui.paper(badge, R.color.ui_mint, 8, 0);
        var params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 4);
        parent.addView(badge, params);
    }

    private void addQuote(LinearLayout parent, Card coach, int index) {
        Context context = parent.getContext();
        TextView quote = Ui.text(context, coach.preview(), 14, R.color.ui_text, false);
        Ui.pad(quote, 10, 8);
        Ui.paper(quote, fill(index), 10, 0);
        quote.setGravity(Gravity.START);
        Ui.block(parent, quote, 0);
    }

    private static int fill(int index) {
        return switch (index % 3) {
            case 0 -> R.color.ui_blue;
            case 1 -> R.color.ui_lilac;
            default -> R.color.ui_pink;
        };
    }

    private static int ink(int index) {
        return switch (index % 3) {
            case 0 -> R.color.ui_ocean;
            case 1 -> R.color.ui_violet;
            default -> R.color.ui_rose;
        };
    }

    private void openCoach(String id) {
        var nav = NavHostFragment.findNavController(this);
        if (nav.getCurrentDestination() == null || nav.getCurrentDestination().getId() != R.id.coach_screen) return;
        Bundle args = new Bundle();
        args.putString(COACH, id);
        nav.navigate(R.id.coach_card_screen, args);
    }
}
