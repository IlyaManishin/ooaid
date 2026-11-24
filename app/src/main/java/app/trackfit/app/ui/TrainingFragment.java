package app.trackfit.app.ui;

import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.MainActivity;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.TrainingViewModel;
import app.trackfit.app.presentation.TrainingViewModel.DayCard;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TrainingFragment extends Fragment {
    private static final String PAGE = "page";
    private static final String DAY_INDEX = "dayIndex";
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tickCountdown;
    private TrainingViewModel model;
    private FragmentTrainingBinding binding;
    private String page;
    private TextView remaining;
    private Instant deadline;
    private AlertDialog reportDialog;
    private java.util.UUID reportId;
    private EditText nameField;
    private EditText minutesField;
    private LocalTime autoStart;

    public TrainingFragment() {
        super(R.layout.fragment_training);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        binding = FragmentTrainingBinding.bind(view);
        page = requireArguments().getString(PAGE);
        model = new ViewModelProvider(requireActivity()).get(TrainingViewModel.class);
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
        model.refresh();
        if (!isEditor()) render();
    }

    @Override
    public void onPause() {
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
        super.onDestroyView();
    }

    private void render() {
        if (binding == null) return;
        timer.removeCallbacks(tick);
        remaining = null;
        deadline = null;
        binding.trainingContent.removeAllViews();
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
        if (home.mode() == TrainingViewModel.Mode.NO_PROGRAM) {
            heading(panel, getString(R.string.program_no_active));
            line(panel, getString(R.string.program_choose_hint));
        } else {
            heading(panel, home.program());
            button(panel, R.string.program_stop, () -> model.stopProgram(this::error));
            heading(panel, getString(R.string.cycle_calendar, home.program()));
            HorizontalScrollView scroll = new HorizontalScrollView(requireContext());
            LinearLayout calendar = new LinearLayout(requireContext());
            calendar.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(calendar);
            panel.addView(scroll);
            for (var item : home.calendar()) {
                LinearLayout card = card(calendar);
                line(card, item.date() + " · " + item.day());
                line(card, item.marker());
            }
            LinearLayout card = card(panel);
            if (home.day() == null) {
                line(card, getString(R.string.cycle_waiting));
            } else {
                heading(card, getString(R.string.day_today, home.day().name()));
                if (!home.day().work()) {
                    line(card, getString(R.string.day_rest_description));
                } else {
                    line(card, getString(R.string.day_status, home.status()));
                    line(card, getString(R.string.day_count, home.day().exercises().size()));
                    line(card, getString(R.string.day_duration_value, home.day().minutes()));
                    if (home.day().autoStart() != null) {
                        line(card, getString(R.string.day_autostart_value, clock(home.day().autoStart())));
                    }
                    if (home.mode() == TrainingViewModel.Mode.READY) {
                        button(card, R.string.workout_start, () -> model.startWorkout(
                                () -> navigateFrom(R.id.home_screen, R.id.workout_screen), this::error));
                    } else if (home.mode() == TrainingViewModel.Mode.RUNNING) {
                        line(card, getString(R.string.workout_checked, home.checked(), home.day().exercises().size()));
                        countdown(card, home.deadline());
                        button(card, R.string.workout_enter,
                                () -> navigateFrom(R.id.home_screen, R.id.workout_screen));
                    } else {
                        line(card, getString(R.string.workout_checked, home.checked(), home.day().exercises().size()));
                    }
                }
            }
        }
        button(panel, R.string.programs_title, () -> navigateFrom(R.id.home_screen, R.id.programs_screen));
        showReport();
    }

    private void showPrograms() {
        LinearLayout panel = binding.trainingContent;
        button(panel, R.string.nav_back, this::back);
        heading(panel, getString(R.string.programs_title));
        line(panel, getString(R.string.programs_description));
        for (var program : model.catalog()) {
            LinearLayout card = card(panel);
            heading(card, program.name());
            LinearLayout row = row(card);
            if (program.editEnabled()) {
                button(row, R.string.edit, () -> model.editProgram(program.id(),
                        () -> navigateFrom(R.id.programs_screen, R.id.program_editor_screen), this::error));
            }
            button(row, R.string.select, () -> pickStart(program.id())).setEnabled(program.startEnabled());
        }
        button(panel, R.string.programs_add, () -> {
            model.newProgram();
            navigateFrom(R.id.programs_screen, R.id.program_editor_screen);
        });
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
        button(panel, R.string.nav_back, this::discardAndBack);
        heading(panel, getString(R.string.programs_title));
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
        button(panel, R.string.nav_back, this::discardAndBack);
        heading(panel, day.name());
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
        var types = model.exerciseTypes();
        String[] labels = new String[types.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(types.get(i).label());
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.exercise_choose_type)
                .setItems(labels, (dialog, index) -> editExercise(types.get(index).key(), day, -1))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void editExercise(String type, int day, int index) {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), dp(8), dp(22), dp(8));
        line(form, getString(R.string.exercise_editor));
        Map<String, Supplier<String>> inputs = new LinkedHashMap<>();
        for (FieldState field : model.exerciseFields(type, day, index)) {
            String label = getString(field.label());
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
        button(panel, R.string.nav_back, this::goHome);
        heading(panel, home.day().name());
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
