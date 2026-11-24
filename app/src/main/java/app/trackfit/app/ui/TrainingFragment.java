package app.trackfit.app.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Layout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.MainActivity;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.CoachViewModel;
import app.trackfit.app.presentation.TrainingViewModel;
import app.trackfit.app.presentation.TrainingViewModel.CalendarStatus;
import app.trackfit.app.presentation.TrainingViewModel.DayCard;
import app.trackfit.app.presentation.TrainingViewModel.ExerciseType;
import app.trackfit.app.presentation.TrainingViewModel.DraftState;
import app.trackfit.app.presentation.TrainingViewModel.FieldState;
import app.trackfit.app.presentation.TrainingViewModel.HomeState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TrainingFragment extends Fragment {
    private static final String PAGE = "page";
    private static final String DAY_INDEX = "dayIndex";
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tickCountdown;
    private TrainingViewModel model;
    private CoachViewModel coach;
    private LinearLayout coachBar;
    private CalendarAnchor calendarAnchor;
    private int calendarScroll = -1;
    private FragmentTrainingBinding binding;
    private String page;
    private TextView remaining;
    private Instant deadline;
    private AlertDialog reportDialog;
    private String reportMessage;
    private java.util.UUID reportId;
    private EditText nameField;
    private EditText minutesField;
    private LocalTime autoStart;

    public TrainingFragment() {
        super(R.layout.fragment_training);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedState) {
        binding = FragmentTrainingBinding.bind(view);
        page = requireArguments().getString(PAGE);
        model = new ViewModelProvider(requireActivity()).get(TrainingViewModel.class);
        if ("home".equals(page)) {
            coach = new ViewModelProvider(requireActivity()).get(CoachViewModel.class);
            coach.state().observe(getViewLifecycleOwner(), state -> {
                renderCoach(state);
                if (reportDialog != null) {
                    String message = state.resultLine() ? reportMessage + "\n\n" + state.line() : reportMessage;
                    reportDialog.setMessage(message);
                }
            });
        }
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(isEditor()) {
                    @Override public void handleOnBackPressed() { discardAndBack(); }
                });
        model.changes().observe(getViewLifecycleOwner(), ignored -> {
            if (!isEditor()) render();
        });
        render();
    }

    private boolean isEditor() { return "program_editor".equals(page) || "day_editor".equals(page); }

    @Override
    public void onResume() {
        super.onResume();
        if (coach != null) coach.homeVisible(true);
        model.refresh();
        if (!isEditor()) render();
    }

    @Override
    public void onPause() {
        if (coach != null) coach.homeVisible(false);
        timer.removeCallbacks(tick);
        detachReport();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        nameField = null;
        minutesField = null;
        remaining = null;
        coachBar = null;
        super.onDestroyView();
    }

    private void render() {
        if (binding == null) return;
        timer.removeCallbacks(tick);
        remaining = null;
        deadline = null;
        binding.trainingContent.removeAllViews();
        binding.screenFooter.removeAllViews();
        switch (Objects.requireNonNull(page)) {
            case "home" -> showHome();
            case "programs" -> showPrograms();
            case "program_editor" -> showProgramEditor();
            case "day_editor" -> showDayEditor();
            case "workout" -> showWorkout();
            default -> throw new IllegalArgumentException("Unknown training page");
        }
        tickCountdown();
    }

    private void showHome() {
        HomeState home = model.home();
        LinearLayout panel = binding.trainingContent;
        if (coach != null) {
            coachBar = Ui.row(requireContext());
            Ui.block(panel, coachBar, 14);
            renderCoach(coach.state().getValue());
        }
        if (home.mode() == TrainingViewModel.Mode.NO_PROGRAM) {
            LinearLayout empty = Ui.column(requireContext());
            Ui.paper(empty, R.color.ui_card, 18, R.color.ui_border);
            Ui.pad(empty, 16, 16);
            empty.addView(Ui.badge(requireContext(), R.drawable.ic_ui_strength,
                    R.color.ui_green, R.color.ui_mint, 44));
            heading(empty, getString(R.string.program_no_active));
            line(empty, getString(R.string.program_choose_hint));
            Ui.block(panel, empty, 0);
        } else {
            LinearLayout header = Ui.row(requireContext());
            TextView title = Ui.text(requireContext(), home.program(), 24, R.color.ui_text, true);
            ViewCompat.setAccessibilityHeading(title, true);
            Ui.weight(header, title, 12);
            header.addView(Ui.button(requireContext(), R.string.program_stop, 0, R.color.ui_mint,
                    R.color.ui_green, () -> model.stopProgram(this::error)));
            Ui.block(panel, header, 12);

            LinearLayout cycle = Ui.column(requireContext());
            Ui.paper(cycle, R.color.ui_card, 18, R.color.ui_border);
            Ui.pad(cycle, 12, 12);
            TextView calendarTitle = Ui.text(requireContext(), getString(R.string.cycle_calendar, home.program()),
                    14, R.color.ui_text, true);
            ViewCompat.setAccessibilityHeading(calendarTitle, true);
            Ui.block(cycle, calendarTitle, 8);
            addCalendar(cycle, home.calendar(), home.program());

            LinearLayout today = Ui.column(requireContext());
            Ui.paper(today, R.color.ui_mint, 14, R.color.ui_green_border);
            Ui.pad(today, 12, 10);
            Ui.block(cycle, today, 0);
            renderToday(today, home);
            Ui.block(panel, cycle, 0);
        }
        setFooter(R.string.programs_title, () -> navigateFrom(R.id.home_screen, R.id.programs_screen));
        showReport();
    }

    private void renderCoach(CoachViewModel.State state) {
        if (coachBar != null) CoachBar.render(coachBar, state, coach::dismiss,
                () -> ((MainActivity) requireActivity()).showCoachSelection());
    }

    private void setFooter(int label, Runnable action) {
        LinearLayout footer = binding.screenFooter;
        footer.setVisibility(View.VISIBLE);
        Ui.block(footer, Ui.button(requireContext(), label, R.drawable.ic_ui_add, R.color.ui_neutral,
                R.color.ui_green, action), 0);
    }

    private void addCalendar(LinearLayout parent, List<TrainingViewModel.CalendarCard> days, String program) {
        HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout calendar = Ui.row(requireContext());
        calendar.setGravity(Gravity.TOP);
        scroll.addView(calendar);
        int current = 0;
        for (int index = 0; index < days.size(); index++) {
            var item = days.get(index);
            if (item.marker() == CalendarStatus.TODAY) current = index;
            calendar.addView(calendarTile(item));
        }
        int firstVisibleDay = Math.max(0, current - 1);
        var anchor = new CalendarAnchor(program, days.isEmpty() ? "" : days.get(current).date());
        if (!anchor.equals(calendarAnchor)) {
            calendarAnchor = anchor;
            calendarScroll = -1;
        }
        int initialScroll = calendarScroll;
        scroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left == oldRight - oldLeft) return;
            int gap = dp(6);
            int width = Math.max(dp(70), (right - left - gap * 3) / 4);
            for (int index = 0; index < calendar.getChildCount(); index++) {
                View tile = calendar.getChildAt(index);
                var params = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT);
                if (index + 1 < calendar.getChildCount()) params.setMarginEnd(gap);
                tile.setLayoutParams(params);
            }
            scroll.post(() -> {
                scroll.scrollTo(initialScroll < 0 ? (width + gap) * firstVisibleDay : initialScroll, 0);
                scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> calendarScroll = x);
            });
        });
        Ui.block(parent, scroll, 10);
    }

    private record CalendarAnchor(String program, String date) {}
    private record CalendarAccent(int fill, int ink, int glyph) {}

    private LinearLayout calendarTile(TrainingViewModel.CalendarCard item) {
        Context context = requireContext();
        CalendarStatus marker = item.marker();
        CalendarAccent accent = switch (marker) {
            case TODAY -> new CalendarAccent(R.color.ui_mint, R.color.ui_green, R.drawable.ic_ui_clock);
            case COMPLETED -> new CalendarAccent(R.color.ui_mint, R.color.ui_green, R.drawable.ic_ui_check);
            case INCOMPLETE -> new CalendarAccent(R.color.ui_lilac, R.color.ui_violet, R.drawable.ic_ui_info);
            case SKIPPED -> new CalendarAccent(R.color.ui_pink, R.color.ui_rose, R.drawable.ic_ui_close);
            case REST -> new CalendarAccent(R.color.ui_blue, R.color.ui_ocean, R.drawable.ic_ui_rest);
            case RUNNING -> new CalendarAccent(R.color.ui_blue, R.color.ui_ocean, R.drawable.ic_ui_clock);
            case LOCKED -> new CalendarAccent(R.color.ui_neutral, R.color.ui_muted, R.drawable.ic_ui_lock);
            case PENDING -> new CalendarAccent(R.color.ui_neutral, R.color.ui_muted, R.drawable.ic_ui_info);
        };
        LinearLayout tile = Ui.column(context);
        tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        Ui.pad(tile, 3, 8);
        Ui.paper(tile, accent.fill(), 12, marker == CalendarStatus.TODAY ? R.color.ui_green_border : R.color.ui_border);
        TextView date = Ui.text(context, item.date(), 13, accent.ink(), true);
        date.setGravity(Gravity.CENTER);
        TextView day = Ui.text(context, item.day(), 11, R.color.ui_text, false);
        day.setGravity(Gravity.CENTER);
        day.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        day.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        day.setMaxLines(2);
        day.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(date);
        tile.addView(day);
        LinearLayout status = Ui.row(context);
        status.setGravity(Gravity.CENTER);
        status.addView(Ui.icon(context, accent.glyph(), accent.ink(), 14));
        if (marker == CalendarStatus.TODAY) {
            status.addView(Ui.text(context, getString(marker.label()), 11, accent.ink(), true));
        }
        tile.addView(status);
        tile.setContentDescription(getString(R.string.ui_calendar_tile, item.date(), item.day(), getString(marker.label())));
        tile.setTooltipText(getString(marker.label()));
        ViewCompat.setScreenReaderFocusable(tile, true);
        date.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        day.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        status.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        return tile;
    }

    private void renderToday(LinearLayout today, HomeState home) {
        if (home.day() == null) {
            line(today, getString(R.string.cycle_waiting));
            return;
        }
        Context context = requireContext();
        TextView title = Ui.text(context, getString(R.string.day_today, home.day().name()),
                16, R.color.ui_green, true);
        ViewCompat.setAccessibilityHeading(title, true);
        Ui.block(today, title, 4);
        if (!home.day().work()) {
            today.addView(Ui.text(context, getString(R.string.day_rest_description), 14, R.color.ui_muted, false));
            return;
        }
        today.addView(Ui.text(context, getString(R.string.day_status, home.status()), 14, R.color.ui_green, true));
        line(today, getString(R.string.day_count, home.day().exercises().size()));
        line(today, getString(R.string.day_duration_value, home.day().minutes()));
        if (home.day().autoStart() != null) {
            line(today, getString(R.string.day_autostart_value, clock(home.day().autoStart())));
        }
        if (home.mode() == TrainingViewModel.Mode.READY) {
            button(today, R.string.workout_start, () -> model.startWorkout(
                    () -> navigateFrom(R.id.home_screen, R.id.workout_screen), this::error));
        } else {
            line(today, getString(R.string.workout_checked, home.checked(), home.day().exercises().size()));
            if (home.mode() == TrainingViewModel.Mode.RUNNING) {
                countdown(today, home.deadline());
                button(today, R.string.workout_enter,
                        () -> navigateFrom(R.id.home_screen, R.id.workout_screen));
            }
        }
    }

    private void showPrograms() {
        Context context = requireContext();
        LinearLayout panel = binding.trainingContent;
        Ui.brand(panel, () -> navigateFrom(R.id.programs_screen, R.id.program_settings_screen));
        ScreenHeader.addTo(panel, getString(R.string.programs_title), this::back);
        Ui.block(panel, Ui.text(context, getString(R.string.programs_description),
                14, R.color.ui_muted, false), 12);
        var catalog = model.catalog();
        int[] fills = {R.color.ui_mint, R.color.ui_lilac, R.color.ui_blue, R.color.ui_pink};
        int[] inks = {R.color.ui_green, R.color.ui_violet, R.color.ui_ocean, R.color.ui_rose};
        for (int index = 0; index < catalog.size(); index++) {
            var program = catalog.get(index);
            int accent = index % fills.length;
            LinearLayout surface = Ui.row(context);
            Ui.pad(surface, 8, 8);
            Ui.paper(surface, R.color.ui_card, 16, R.color.ui_border);

            LinearLayout entry = Ui.row(context);
            entry.addView(Ui.badge(context, R.drawable.ic_ui_strength, inks[accent], fills[accent], 46));
            LinearLayout copy = Ui.column(context);
            Ui.pad(copy, 8, 2);
            copy.addView(Ui.text(context, program.name(), 17, R.color.ui_text, true));
            int subtitle = !program.editEnabled() ? R.string.ui_program_current
                    : program.startEnabled() ? R.string.ui_program_saved : R.string.ui_program_blocked;
            copy.addView(Ui.text(context, getString(subtitle), 13, R.color.ui_muted, false));
            Ui.weight(entry, copy, 0);
            if (program.startEnabled()) {
                entry.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_muted, 18));
                entry.setMinimumHeight(dp(48));
                Ui.tap(entry, R.color.ui_card, 12, 0, () -> pickStart(program.id()));
            }
            Ui.weight(surface, entry, 4);
            if (program.editEnabled()) {
                surface.addView(Ui.iconButton(context, R.drawable.ic_ui_edit, R.string.edit,
                        R.color.ui_muted, R.color.ui_neutral, () -> model.editProgram(program.id(),
                                () -> navigateFrom(R.id.programs_screen, R.id.program_editor_screen), this::error)));
            }
            Ui.block(panel, surface, 8);
        }
        LinearLayout add = Ui.row(context);
        add.setGravity(Gravity.CENTER);
        Ui.pad(add, 12, 12);
        Ui.tap(add, R.color.ui_mint, 14, 0, () -> {
            model.newProgram();
            navigateFrom(R.id.programs_screen, R.id.program_editor_screen);
        });
        GradientDrawable outline = Ui.surface(context, R.color.ui_mint, 14, 0);
        outline.setStroke(dp(1), Ui.color(context, R.color.ui_green_border), dp(5), dp(4));
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(0x245EA48B), outline, null));
        add.setMinimumHeight(dp(48));
        add.addView(Ui.icon(context, R.drawable.ic_ui_add, R.color.ui_green, 22));
        TextView label = Ui.text(context, getString(R.string.programs_add), 14, R.color.ui_green, true);
        Ui.pad(label, 8, 0);
        add.addView(label);
        Ui.block(panel, add, 0);
    }

    private void pickStart(String program) {
        model.startDays(program, days -> {
            if (!isResumed()) return;
            int[] selected = {0};
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.start_day_title)
                    .setSingleChoiceItems(days.toArray(new String[0]), 0, (dialog, index) -> selected[0] = index)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.program_start, (dialog, ignored) ->
                            model.startProgram(program, selected[0], () -> {
                                if (!isResumed()) return;
                                ((MainActivity) requireActivity()).offerNotificationPermission();
                                goHome();
                            }, this::error))
                    .show();
        }, this::error);
    }

    private void showProgramEditor() {
        DraftState draft = model.draft();
        if (draft == null) {
            binding.getRoot().post(this::back);
            return;
        }
        LinearLayout panel = binding.trainingContent;
        ScreenHeader.addTo(panel, getString(R.string.programs_title), this::discardAndBack);
        nameField = field(panel, getString(R.string.program_name), draft.name(), InputType.TYPE_CLASS_TEXT);
        heading(panel, getString(R.string.program_days));
        for (int i = 0; i < draft.days().size(); i++) {
            int index = i;
            DayCard day = draft.days().get(i);
            LinearLayout card = card(panel);
            heading(card, getString(R.string.program_day_number, i + 1, day.name()));
            if (day.work()) {
                line(card, getString(R.string.day_count, day.exercises().size()));
                line(card, day.autoStart() == null ? getString(R.string.day_autostart_disabled)
                        : getString(R.string.day_autostart_value, clock(day.autoStart())));
                button(card, R.string.edit, () -> {
                    saveName();
                    model.editDay();
                    editDay(index);
                });
            }
            LinearLayout actions = row(card);
            if (index > 0) button(actions, R.string.move_up, () -> moveDay(index, index - 1));
            if (index + 1 < draft.days().size()) button(actions, R.string.move_down, () -> moveDay(index, index + 1));
            button(actions, R.string.delete, () -> {
                saveName();
                model.removeDay(index);
                render();
            });
        }
        button(panel, R.string.program_add_work, () -> {
            saveName();
            editDay(model.addWork());
        });
        button(panel, R.string.program_add_rest, () -> {
            saveName();
            model.addRest();
            render();
        });
        button(panel, R.string.save, () -> {
            saveName();
            model.saveProgram(this::back, this::error);
        });
    }

    private void editDay(int index) {
        Bundle arguments = new Bundle();
        arguments.putInt(DAY_INDEX, index);
        if (current(R.id.program_editor_screen)) nav().navigate(R.id.day_editor_screen, arguments);
    }

    private void moveDay(int from, int to) {
        saveName();
        model.moveDay(from, to);
        render();
    }

    private void saveName() { model.renameProgram(value(nameField)); }

    private void showDayEditor() {
        DraftState draft = model.draft();
        int dayIndex = requireArguments().getInt(DAY_INDEX);
        if (draft == null || dayIndex >= draft.days().size()) {
            binding.getRoot().post(this::back);
            return;
        }
        DayCard day = draft.days().get(dayIndex);
        autoStart = day.autoStart();
        LinearLayout panel = binding.trainingContent;
        ScreenHeader.addTo(panel, day.name(), this::discardAndBack);
        line(panel, getString(R.string.day_edit_description));
        heading(panel, getString(R.string.day_settings));
        nameField = field(panel, getString(R.string.day_name), day.name(), InputType.TYPE_CLASS_TEXT);
        minutesField = field(panel, getString(R.string.day_duration),
                day.minutes() > 0 ? Integer.toString(day.minutes()) : "", InputType.TYPE_CLASS_NUMBER);
        line(panel, autoStart == null ? getString(R.string.day_autostart_disabled)
                : getString(R.string.day_autostart_value, clock(autoStart)));
        button(panel, R.string.day_autostart, () -> {
            LocalTime initial = autoStart == null ? LocalTime.of(18, 30) : autoStart;
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                autoStart = LocalTime.of(hour, minute);
                captureDay(dayIndex);
                render();
            }, initial.getHour(), initial.getMinute(), true).show();
        });
        if (autoStart != null) button(panel, R.string.day_clear_autostart, () -> {
            autoStart = null;
            captureDay(dayIndex);
            render();
        });
        for (int i = 0; i < day.exercises().size(); i++) {
            int exerciseIndex = i;
            var exercise = day.exercises().get(i);
            LinearLayout card = card(panel);
            heading(card, exercise.name());
            line(card, exercise.type());
            if (!exercise.parameters().isEmpty()) line(card, exercise.parameters());
            LinearLayout row = row(card);
            button(row, R.string.edit, () -> {
                captureDay(dayIndex);
                editExercise(model.exerciseTypeKey(dayIndex, exerciseIndex), dayIndex, exerciseIndex);
            });
            button(row, R.string.delete, () -> {
                captureDay(dayIndex);
                model.removeExercise(dayIndex, exerciseIndex);
                render();
            });
        }
        button(panel, R.string.exercise_add, () -> {
            captureDay(dayIndex);
            chooseExerciseType(dayIndex);
        });
        button(panel, R.string.done, () -> {
            captureDay(dayIndex);
            model.saveDay();
            back();
        });
    }

    private void captureDay(int day) {
        model.updateDay(day, value(nameField), value(minutesField), autoStart);
    }

    private void chooseExerciseType(int day) {
        Context context = requireContext();
        LinearLayout form = Ui.column(context);
        Ui.pad(form, 14, 12);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(form);
        AlertDialog modal = Ui.formDialog(scroll);
        Ui.dialogHeader(form, R.string.exercise_choose_type, modal::dismiss);
        Ui.block(form, Ui.text(context, getString(R.string.ui_exercise_explanation),
                14, R.color.ui_muted, false), 12);

        var types = model.exerciseTypes();
        for (int index = 0; index < types.size(); index += 2) {
            LinearLayout row = Ui.row(context);
            row.setGravity(Gravity.TOP);
            for (int column = 0; column < 2 && index + column < types.size(); column++) {
                var type = types.get(index + column);
                LinearLayout card = exerciseTile(type, () -> {
                    modal.dismiss();
                    editExercise(type.key(), day, -1);
                });
                Ui.weight(row, card, column == 0 ? 8 : 0);
            }
            Ui.block(form, row, 8);
        }
        Ui.block(form, Ui.button(context, R.string.cancel, 0, R.color.ui_lilac, R.color.ui_text,
                modal::dismiss), 0);
        Ui.showFormDialog(modal);
    }

    private record ExerciseAccent(int icon, int fill, int ink, int description) {}

    private LinearLayout exerciseTile(ExerciseType type, Runnable choose) {
        ExerciseAccent accent = switch (type.key()) {
            case "STRENGTH" -> new ExerciseAccent(R.drawable.ic_ui_strength,
                    R.color.ui_mint, R.color.ui_green, R.string.ui_exercise_strength);
            case "BALANCE" -> new ExerciseAccent(R.drawable.ic_ui_balance,
                    R.color.ui_lilac, R.color.ui_violet, R.string.ui_exercise_balance);
            case "STRETCH" -> new ExerciseAccent(R.drawable.ic_ui_stretch,
                    R.color.ui_pink, R.color.ui_rose, R.string.ui_exercise_stretch);
            case "CARDIO" -> new ExerciseAccent(R.drawable.ic_ui_cardio,
                    R.color.ui_blue, R.color.ui_ocean, R.string.ui_exercise_cardio);
            default -> throw new IllegalArgumentException("Unknown exercise type label");
        };
        Context context = requireContext();
        LinearLayout tile = Ui.column(context);
        tile.setGravity(Gravity.CENTER);
        tile.setMinimumHeight(dp(116));
        Ui.pad(tile, 8, 10);
        Ui.tap(tile, accent.fill(), 14, 0, choose);
        tile.addView(Ui.icon(context, accent.icon(), accent.ink(), 32));
        TextView title = Ui.text(context, getString(type.label()), 16, R.color.ui_text, true);
        Ui.pad(title, 0, 4);
        title.setGravity(Gravity.CENTER);
        tile.addView(title);
        TextView hint = Ui.text(context, getString(accent.description()), 12, R.color.ui_muted, false);
        hint.setGravity(Gravity.CENTER);
        tile.addView(hint);
        return tile;
    }

    private void editExercise(String type, int day, int index) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(8));
        line(form, getString(R.string.exercise_editor));
        Map<String, Supplier<String>> inputs = new LinkedHashMap<>();
        for (FieldState field : model.exerciseFields(type, day, index)) {
            String label = getString(field.kind() == TrainingViewModel.InputKind.DURATION
                    ? R.string.exercise_duration_input : field.label());
            if (field.required()) label = getString(R.string.field_required, label);
            if (field.kind() == TrainingViewModel.InputKind.SIDE) {
                line(form, label);
                Spinner sides = new Spinner(requireContext());
                String[] keys = {"", "LEFT", "RIGHT", "BOTH"};
                sides.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                        new String[]{getString(R.string.field_side_none), getString(R.string.field_side_left),
                                getString(R.string.field_side_right), getString(R.string.field_side_both)}));
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i].equals(field.value())) sides.setSelection(i);
                }
                form.addView(sides);
                inputs.put(field.key(), () -> keys[sides.getSelectedItemPosition()]);
            } else {
                int inputType = switch (field.kind()) {
                    case INTEGER -> InputType.TYPE_CLASS_NUMBER;
                    case DECIMAL -> InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
                    default -> InputType.TYPE_CLASS_TEXT;
                };
                EditText input = field(form, label, field.value(), inputType);
                inputs.put(field.key(), () -> value(input));
            }
        }
        TextView error = line(form, getString(R.string.error_exercise));
        error.setVisibility(View.GONE);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setView(scroll)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Map<String, String> values = new LinkedHashMap<>();
            inputs.forEach((key, field) -> values.put(key, field.get()));
            if (model.saveExercise(type, day, index, values)) {
                dialog.dismiss();
                render();
            } else {
                error.setVisibility(View.VISIBLE);
            }
        }));
        dialog.show();
    }

    private void showWorkout() {
        HomeState home = model.home();
        if (home.mode() != TrainingViewModel.Mode.RUNNING || home.day() == null) {
            if (isResumed() && model.loaded()) binding.getRoot().post(this::goHome);
            return;
        }
        LinearLayout panel = binding.trainingContent;
        ScreenHeader.addTo(panel, home.day().name(), this::goHome);
        line(panel, getString(R.string.workout_running, home.checked(), home.day().exercises().size()));
        countdown(panel, home.deadline());
        for (var exercise : home.day().exercises()) {
            LinearLayout card = card(panel);
            MaterialCheckBox check = new MaterialCheckBox(requireContext());
            check.setText(exercise.name());
            check.setChecked(exercise.checked());
            check.setEnabled(!exercise.checked());
            check.setMinHeight(dp(48));
            card.addView(check);
            if (exercise.checked()) {
                card.setAlpha(0.6f);
            } else {
                check.setOnCheckedChangeListener((view, checked) -> {
                    if (!checked) return;
                    view.setEnabled(false);
                    model.checkExercise(exercise.id(), message -> {
                        error(message);
                        render();
                    });
                });
            }
            line(card, exercise.type());
            if (!exercise.parameters().isEmpty()) line(card, exercise.parameters());
            if (exercise.note() != null) line(card, exercise.note());
        }
        line(panel, getString(R.string.workout_check_hint));
        button(panel, R.string.workout_finish, () -> model.finishWorkout(this::goHome, this::error));
    }

    private void showReport() {
        if (!isResumed()) return;
        var report = model.nextReport();
        if (report == null) {
            detachReport();
            return;
        }
        if (report.id().equals(reportId)) return;
        detachReport();
        reportId = report.id();
        reportMessage = report.message();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext()).setMessage(report.message());
        if (report.cycle()) {
            builder.setCancelable(false)
                    .setPositiveButton(R.string.report_change_program, (dialog, which) -> model.decideCycle(false,
                            () -> navigateFrom(R.id.home_screen, R.id.programs_screen), this::reportError))
                    .setNegativeButton(R.string.report_next_cycle, (dialog, which) -> model.decideCycle(true,
                            () -> {}, this::reportError));
        } else {
            builder.setPositiveButton(R.string.close, (dialog, which) -> model.dismissReport(report.id(), this::reportError))
                    .setOnCancelListener(dialog -> model.dismissReport(report.id(), this::reportError));
        }
        reportDialog = builder.show();
        if (coach != null) coach.reportPresented(report.id());
    }

    private void reportError(String message) {
        error(message);
        detachReport();
        showReport();
    }

    private void detachReport() {
        if (reportDialog != null) reportDialog.dismiss();
        reportDialog = null;
        reportId = null;
    }

    private void countdown(LinearLayout parent, Instant expires) {
        if (expires == null) return;
        deadline = expires;
        remaining = line(parent, "");
    }

    private void tickCountdown() {
        if (remaining == null || deadline == null || !isResumed()) return;
        remaining.setText(getString(R.string.workout_remaining, model.countdown(deadline)));
        timer.postDelayed(tick, 1000);
    }

    private void discardAndBack() {
        if ("program_editor".equals(page)) model.discardProgram();
        if ("day_editor".equals(page)) model.discardDay();
        back();
    }

    private void navigateFrom(int source, int destination) {
        if (current(source)) nav().navigate(destination);
    }

    private boolean current(int destination) {
        if (!isResumed() || binding == null) return false;
        var current = nav().getCurrentDestination();
        return current != null && current.getId() == destination;
    }

    private void back() {
        if (binding != null && isResumed()) nav().popBackStack();
    }

    private void goHome() {
        if (binding != null && isResumed()) nav().popBackStack(R.id.home_screen, false);
    }

    private NavController nav() { return NavHostFragment.findNavController(this); }

    private void error(String message) {
        if (isAdded() && binding != null) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }

    private TextView heading(LinearLayout parent, String text) {
        TextView view = line(parent, text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        androidx.core.view.ViewCompat.setAccessibilityHeading(view, true);
        return view;
    }

    private TextView line(LinearLayout parent, String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view);
        return view;
    }

    private MaterialButton button(LinearLayout parent, int label, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        boolean horizontal = parent.getOrientation() == LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                horizontal ? 0 : LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
                horizontal ? 1 : 0);
        if (horizontal) params.setMarginEnd(dp(4));
        parent.addView(button, params);
        return button;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row);
        return row;
    }

    private LinearLayout card(LinearLayout parent) {
        MaterialCardView surface = new MaterialCardView(requireContext());
        surface.setCardBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(parent,
                com.google.android.material.R.attr.colorSurfaceContainer)));
        surface.setRadius(dp(16));
        LinearLayout content = new LinearLayout(requireContext());
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setOrientation(LinearLayout.VERTICAL);
        surface.addView(content);
        boolean horizontal = parent.getOrientation() == LinearLayout.HORIZONTAL;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                horizontal ? dp(148) : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (horizontal) params.setMarginEnd(dp(8));
        params.bottomMargin = dp(10);
        parent.addView(surface, params);
        return content;
    }

    private EditText field(LinearLayout parent, String label, String initial, int inputType) {
        TextInputLayout wrapper = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        wrapper.setHint(label);
        TextInputEditText field = new TextInputEditText(wrapper.getContext());
        field.setInputType(inputType);
        field.setText(initial);
        field.setMinHeight(dp(48));
        wrapper.addView(field);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        parent.addView(wrapper, params);
        return field;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String value(EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private static String clock(LocalTime time) { return time.format(DateTimeFormatter.ofPattern("HH:mm")); }
}
