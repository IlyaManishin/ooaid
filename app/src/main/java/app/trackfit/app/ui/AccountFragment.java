package app.trackfit.app.ui;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.MainActivity;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.AccountViewModel;
import app.trackfit.app.presentation.CoachViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountFragment extends Fragment {
    private static final String RECORD_ID = "recordId";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);
    private AccountViewModel account;
    private CoachViewModel coach;
    private LinearLayout content;
    private LinearLayout coachBar;
    private TextView coachName;
    private TextView photoStatus;
    private Uri photo;
    private LocalDate birthday;
    private String page;
    private boolean editing;
    private boolean recordDialogOpen;

    private void onPhotoPicked(Uri uri) {
        if (uri == null) return;
        photo = uri;
        if (photoStatus != null) photoStatus.setText(R.string.profile_photo_selected);
    }

    public AccountFragment() {
        super(R.layout.fragment_training);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        content = FragmentTrainingBinding.bind(view).trainingContent;
        page = requireArguments().getString("page");
        account = new ViewModelProvider(requireActivity()).get(AccountViewModel.class);
        coach = new ViewModelProvider(requireActivity()).get(CoachViewModel.class);
        coach.state().observe(getViewLifecycleOwner(), this::renderCoach);
        account.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        account.refresh();
    }

    @Override
    public void onDestroyView() {
        content = null;
        coachBar = null;
        coachName = null;
        photoStatus = null;
        editing = false;
        super.onDestroyView();
    }

    private void render(AccountViewModel.State state) {
        if ("profile_editor".equals(page) && (editing || !state.loaded())) return;
        content.removeAllViews();
        coachBar = null;
        coachName = null;
        switch (page) {
            case "profile" -> showProfile(state.profile());
            case "profile_editor" -> editProfile(state.profile());
            case "settings" -> showSettings(state.pushEnabled());
            case "records" -> showRecords(state.records());
            case "record" -> showRecord(state.records());
            default -> throw new IllegalStateException("Unknown account screen");
        }
        renderCoach(coach.state().getValue());
    }

    private void showProfile(AccountViewModel.ProfileCard profile) {
        heading(R.string.profile_title);
        addAvatar(profile);
        text(content, profile.name().isEmpty() ? getString(R.string.profile_default_name) : profile.name(), true);
        String[] options = sexOptions();
        text(content, getString(R.string.profile_sex_value, options[profile.sex()]), false);
        text(content, getString(R.string.profile_age_value, profile.age() == null
                ? getString(R.string.profile_unspecified) : Integer.toString(profile.age())), false);
        coachName = text(content, "", false);
        action(content, R.string.profile_records, () -> navigate(R.id.records_screen));
        action(content, R.string.profile_settings, () -> navigate(R.id.settings_screen));
        action(content, R.string.profile_edit, () -> navigate(R.id.profile_editor_screen));
    }

    private void editProfile(AccountViewModel.ProfileCard profile) {
        editing = true;
        backAndTitle(R.string.profile_edit_title);
        addAvatar(profile);
        photoStatus = text(content, photo == null ? "" : getString(R.string.profile_photo_selected), false);
        action(content, R.string.profile_choose_photo, () -> photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        EditText name = field(content, R.string.profile_name, profile.name());
        text(content, getString(R.string.profile_sex), true);
        Spinner sex = new Spinner(requireContext());
        sex.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, sexOptions()));
        sex.setSelection(profile.sex());
        content.addView(sex);
        birthday = profile.birthday();
        TextView date = text(content, dateLabel(), false);
        action(content, R.string.profile_choose_birthdate, () -> {
            LocalDate today = account.today();
            LocalDate selected = birthday == null ? today : birthday;
            DatePickerDialog picker = new DatePickerDialog(requireContext(), (view, y, m, d) -> {
                birthday = LocalDate.of(y, m + 1, d);
                date.setText(dateLabel());
            }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
            picker.show();
        });
        action(content, R.string.profile_clear_birthdate, () -> {
            birthday = null;
            date.setText(dateLabel());
        });
        action(content, R.string.save, () -> account.saveProfile(value(name), sex.getSelectedItemPosition(),
                birthday, photo, this::back, this::error));
    }

    private void showSettings(boolean pushEnabled) {
        backAndTitle(R.string.profile_settings);
        action(content, R.string.tab_coach, () -> ((MainActivity) requireActivity()).showCoachSelection());
        MaterialSwitch push = new MaterialSwitch(requireContext());
        push.setText(R.string.profile_push);
        push.setMinHeight(dp(48));
        push.setChecked(pushEnabled);
        push.setOnCheckedChangeListener((view, checked) -> account.setPushEnabled(checked, message -> {
            error(message);
            account.refresh();
            render(account.state().getValue());
        }));
        content.addView(push);
    }

    private void showRecords(List<AccountViewModel.Record> records) {
        backAndTitle(R.string.record_title);
        addCoachBar();
        for (var record : records) {
            StringBuilder details = new StringBuilder(record.exercise());
            for (var parameter : record.parameters()) {
                details.append("\n").append(getString(R.string.field_value, parameter.name(), parameter.value()));
            }
            details.append("\n").append(getString(R.string.record_date, record.date().format(DATE)));
            action(content, details.toString(), () -> {
                Bundle arguments = new Bundle();
                arguments.putString(RECORD_ID, record.id().toString());
                if (isResumed()) NavHostFragment.findNavController(this).navigate(R.id.record_screen, arguments);
            });
        }
        action(content, R.string.record_add, () -> editRecord(null));
    }

    private void showRecord(List<AccountViewModel.Record> records) {
        backAndTitle(R.string.record_title);
        addCoachBar();
        UUID id = UUID.fromString(requireArguments().getString(RECORD_ID));
        for (var record : records) {
            if (!record.id().equals(id)) continue;
            text(content, record.exercise(), true);
            heading(R.string.record_parameters);
            for (var parameter : record.parameters()) {
                text(content, getString(R.string.field_value, parameter.name(), parameter.value()), false);
            }
            text(content, getString(R.string.record_date, record.date().format(DATE)), false);
            action(content, R.string.record_edit, () -> editRecord(record));
            action(content, R.string.record_delete, () -> account.deleteRecord(record.id(), this::back, this::error));
        }
    }

    private record ParameterInputs(EditText name, EditText value) {}

    private void editRecord(AccountViewModel.Record record) {
        if (recordDialogOpen) return;
        recordDialogOpen = true;
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText exercise = field(form, R.string.record_exercise, record == null ? "" : record.exercise());
        text(form, getString(R.string.record_parameters), true);
        LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        form.addView(rows);
        List<ParameterInputs> parameters = new ArrayList<>();
        if (record != null) {
            for (var param : record.parameters()) addParameter(rows, parameters, param.name(), param.value());
        }
        action(form, R.string.record_add_parameter, () -> addParameter(rows, parameters, "", ""));
        ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(form);
        AlertDialog modal = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(record == null ? R.string.record_add : R.string.record_edit)
                .setView(scroll).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null)
                .create();
        modal.setOnDismissListener(ignored -> recordDialogOpen = false);
        modal.setOnShowListener(ignored -> {
            var save = modal.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(view -> {
                var values = new ArrayList<AccountViewModel.Parameter>();
                for (var input : parameters) {
                    values.add(new AccountViewModel.Parameter(value(input.name()), value(input.value())));
                }
                save.setEnabled(false);
                account.saveRecord(record == null ? null : record.id(), value(exercise), values, modal::dismiss, message -> {
                    save.setEnabled(true);
                    error(message);
                });
            });
        });
        modal.show();
    }

    private void addParameter(LinearLayout rows, List<ParameterInputs> inputs, String name, String value) {
        LinearLayout pair = new LinearLayout(requireContext());
        pair.setOrientation(LinearLayout.VERTICAL);
        var parameter = new ParameterInputs(field(pair, R.string.record_parameter_name, name),
                field(pair, R.string.record_parameter_value, value));
        inputs.add(parameter);
        action(pair, R.string.delete, () -> {
            inputs.remove(parameter);
            rows.removeView(pair);
        });
        rows.addView(pair);
    }

    private void addAvatar(AccountViewModel.ProfileCard profile) {
        ImageView avatar = new ImageView(requireContext());
        avatar.setContentDescription(getString(R.string.profile_avatar));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (profile.avatar() == null) avatar.setImageResource(R.drawable.ic_tab_coach);
        else avatar.setImageBitmap(profile.avatar());
        content.addView(avatar, new LinearLayout.LayoutParams(dp(84), dp(84)));
    }

    private void addCoachBar() {
        coachBar = new LinearLayout(requireContext());
        content.addView(coachBar);
    }

    private void renderCoach(CoachViewModel.State state) {
        if (state == null || state.selected() == null) return;
        if (coachBar != null) CoachBar.render(coachBar, state, coach::dismiss);
        if (coachName != null) coachName.setText(getString(R.string.profile_trainer, state.selected().name()));
    }

    private String dateLabel() {
        return getString(R.string.profile_birthdate, birthday == null
                ? getString(R.string.profile_unspecified) : birthday.format(DATE));
    }

    private String[] sexOptions() {
        return new String[]{getString(R.string.profile_unspecified), getString(R.string.profile_male),
                getString(R.string.profile_female)};
    }

    private void backAndTitle(int title) {
        ScreenHeader.addTo(content, getString(title), this::back);
    }

    private void heading(int title) { text(content, getString(title), true); }

    private TextView text(LinearLayout parent, String value, boolean title) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setPadding(0, dp(6), 0, dp(6));
        view.setTextAppearance(title ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
                : com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        parent.addView(view);
        return view;
    }

    private void action(LinearLayout parent, int label, Runnable action) {
        action(parent, getString(label), action);
    }

    private void action(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }

    private EditText field(LinearLayout parent, int label, String value) {
        TextInputLayout wrapper = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        wrapper.setHint(label);
        TextInputEditText input = new TextInputEditText(wrapper.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(value);
        wrapper.addView(input);
        parent.addView(wrapper);
        return input;
    }

    private static String value(EditText input) { return input.getText() == null ? "" : input.getText().toString(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void navigate(int id) { if (isResumed()) NavHostFragment.findNavController(this).navigate(id); }
    private void back() { if (isResumed()) NavHostFragment.findNavController(this).popBackStack(); }
    private void error(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
}
