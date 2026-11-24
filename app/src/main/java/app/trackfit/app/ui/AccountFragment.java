package app.trackfit.app.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import app.trackfit.app.MainActivity;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTrainingBinding;
import app.trackfit.app.presentation.AccountViewModel;
import app.trackfit.app.presentation.CoachViewModel;
import com.google.android.material.button.MaterialButton;
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
    private static final String OPEN_PHOTO = "openPhoto";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onPhotoPicked);
    private AccountViewModel account;
    private CoachViewModel coach;
    private LinearLayout content;
    private LinearLayout footer;
    private LinearLayout coachBar;
    private TextView coachName;
    private TextView photoStatus;
    private Uri photo;
    private LocalDate birthday;
    private String page;
    private boolean editing;
    private boolean recordDialogOpen;
    private boolean openPhotoOnEditor;

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
        FragmentTrainingBinding binding = FragmentTrainingBinding.bind(view);
        content = binding.trainingContent;
        footer = binding.screenFooter;
        openPhotoOnEditor = saved == null && requireArguments().getBoolean(OPEN_PHOTO);
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
        footer = null;
        coachBar = null;
        coachName = null;
        photoStatus = null;
        editing = false;
        super.onDestroyView();
    }

    private void render(AccountViewModel.State state) {
        if ("profile_editor".equals(page) && (editing || !state.loaded())) return;
        content.removeAllViews();
        footer.removeAllViews();
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
        Context context = requireContext();
        Runnable openEditor = () -> navigate(R.id.profile_editor_screen);
        Runnable openSettings = () -> navigate(R.id.settings_screen);
        Ui.brand(content, openSettings);

        LinearLayout identity = Ui.row(context);
        identity.setGravity(Gravity.TOP);
        Ui.pad(identity, 12, 12);
        Ui.paper(identity, R.color.ui_card, 18, R.color.ui_border);

        FrameLayout avatarPanel = new FrameLayout(context);
        ImageView avatar = avatar(profile);
        avatarPanel.addView(avatar, new FrameLayout.LayoutParams(dp(94), dp(104)));
        ImageButton camera = Ui.iconButton(context, R.drawable.ic_ui_camera, R.string.profile_choose_photo,
                R.color.ui_text, R.color.ui_card, () -> {
                    Bundle args = new Bundle();
                    args.putBoolean(OPEN_PHOTO, true);
                    if (isResumed()) NavHostFragment.findNavController(this)
                            .navigate(R.id.profile_editor_screen, args);
                });
        camera.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        camera.setBackground(new InsetDrawable(camera.getBackground(), dp(7)));
        Ui.pad(camera, 14, 14);
        avatarPanel.addView(camera, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.END));
        Ui.inline(identity, avatarPanel, 10);

        LinearLayout details = Ui.column(context);
        String displayName = profile.name().isEmpty() ? getString(R.string.profile_default_name) : profile.name();
        TextView title = Ui.text(context, displayName, 23, R.color.ui_text, true);
        ViewCompat.setAccessibilityHeading(title, true);
        Ui.block(details, title, 8);
        String gender = getString(R.string.profile_sex_value, sexOptions()[profile.sex()]);
        profileDetail(details, R.drawable.ic_ui_person, gender, openEditor);
        String age = getString(R.string.profile_age_value, profile.age() == null
                ? getString(R.string.profile_unspecified) : Integer.toString(profile.age()));
        profileDetail(details, R.drawable.ic_ui_calendar, age, openEditor);
        coachName = profileDetail(details, R.drawable.ic_tab_coach, getString(R.string.ui_profile_no_coach),
                this::openCoachSelection);
        Ui.weight(identity, details, 0);
        Ui.block(content, identity, 12);

        LinearLayout shortcuts = Ui.row(context);
        shortcuts.setGravity(Gravity.TOP);
        Ui.weight(shortcuts, profileShortcut(R.string.profile_records, R.string.ui_profile_records_hint,
                R.drawable.ic_ui_trophy, R.color.ui_mint, R.color.ui_green, () -> navigate(R.id.records_screen)), 8);
        Ui.weight(shortcuts, profileShortcut(R.string.profile_settings, R.string.ui_profile_settings_hint,
                R.drawable.ic_ui_settings, R.color.ui_lilac, R.color.ui_violet, openSettings), 0);
        Ui.block(content, shortcuts, 12);

        LinearLayout edit = Ui.row(context);
        edit.setMinimumHeight(dp(52));
        Ui.pad(edit, 12, 8);
        Ui.tap(edit, R.color.ui_mint, 14, R.color.ui_border, openEditor);
        edit.addView(Ui.icon(context, R.drawable.ic_ui_edit, R.color.ui_green, 22));
        TextView label = Ui.text(context, getString(R.string.profile_edit), 14, R.color.ui_green, true);
        Ui.pad(label, 10, 0);
        Ui.weight(edit, label, 0);
        edit.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_green, 20));
        Ui.block(content, edit, 0);
    }

    private TextView profileDetail(LinearLayout parent, int icon, String label, Runnable open) {
        Context context = parent.getContext();
        LinearLayout row = Ui.row(context);
        row.setMinimumHeight(dp(48));
        Ui.pad(row, 6, 4);
        Ui.tap(row, R.color.ui_neutral, 10, 0, open);
        row.addView(Ui.icon(context, icon, R.color.ui_muted, 18));
        TextView value = Ui.text(context, label, 13, R.color.ui_text, false);
        Ui.pad(value, 6, 0);
        Ui.weight(row, value, 0);
        row.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_muted, 16));
        Ui.block(parent, row, 4);
        return value;
    }

    private LinearLayout profileShortcut(int title, int hint, int icon, int fill, int ink, Runnable action) {
        Context context = requireContext();
        LinearLayout shortcut = Ui.column(context);
        Ui.pad(shortcut, 12, 12);
        Ui.tap(shortcut, fill, 16, R.color.ui_border, action);
        shortcut.setMinimumHeight(dp(112));
        shortcut.addView(Ui.icon(context, icon, ink, 24));
        TextView caption = Ui.text(context, getString(title), 15, R.color.ui_text, true);
        Ui.pad(caption, 0, 4);
        LinearLayout row = Ui.row(context);
        Ui.weight(row, caption, 0);
        row.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_muted, 16));
        Ui.block(shortcut, row, 0);
        shortcut.addView(Ui.text(context, getString(hint), 12, R.color.ui_muted, false));
        return shortcut;
    }

    private void editProfile(AccountViewModel.ProfileCard profile) {
        editing = true;
        backAndTitle(R.string.profile_edit_title);
        addAvatar(profile);
        photoStatus = text(content, photo == null ? "" : getString(R.string.profile_photo_selected), false);
        action(content, R.string.profile_choose_photo, this::choosePhoto);
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
        if (openPhotoOnEditor) {
            openPhotoOnEditor = false;
            choosePhoto();
        }
    }

    private void choosePhoto() {
        photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void openCoachSelection() {
        ((MainActivity) requireActivity()).showCoachSelection();
    }

    private void showSettings(boolean pushEnabled) {
        backAndTitle(R.string.profile_settings);
        action(content, R.string.tab_coach, this::openCoachSelection);
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
        Context context = requireContext();
        addCoachBar();
        backAndTitle(R.string.record_title);
        if (records.isEmpty()) {
            LinearLayout empty = Ui.column(context);
            Ui.pad(empty, 14, 14);
            Ui.paper(empty, R.color.ui_card, 16, R.color.ui_border);
            empty.addView(Ui.badge(context, R.drawable.ic_ui_trophy, R.color.ui_green, R.color.ui_mint, 44));
            TextView hint = Ui.text(context, getString(R.string.ui_records_empty), 14, R.color.ui_muted, false);
            Ui.pad(hint, 0, 8);
            empty.addView(hint);
            Ui.block(content, empty, 0);
        }
        for (var record : records) {
            LinearLayout card = Ui.row(context);
            Ui.pad(card, 14, 12);
            Ui.tap(card, R.color.ui_card, 16, R.color.ui_border, () -> {
                Bundle arguments = new Bundle();
                arguments.putString(RECORD_ID, record.id().toString());
                if (isResumed()) NavHostFragment.findNavController(this).navigate(R.id.record_screen, arguments);
            });
            LinearLayout copy = Ui.column(context);
            Ui.block(copy, Ui.text(context, record.exercise(), 17, R.color.ui_text, true), 3);
            List<String> parameters = new ArrayList<>();
            for (var parameter : record.parameters()) {
                parameters.add(getString(R.string.field_value, parameter.name(), parameter.value()));
            }
            if (!parameters.isEmpty()) {
                Ui.block(copy, Ui.text(context, String.join(getString(R.string.ui_separator), parameters), 14,
                        R.color.ui_muted, false), 4);
            }
            LinearLayout date = Ui.row(context);
            date.addView(Ui.icon(context, R.drawable.ic_ui_calendar, R.color.ui_muted, 16));
            String formattedDate = record.date().format(DATE);
            TextView dateLabel = Ui.text(context, formattedDate, 13, R.color.ui_muted, false);
            Ui.pad(dateLabel, 5, 0);
            dateLabel.setContentDescription(getString(R.string.record_date, formattedDate));
            date.addView(dateLabel);
            Ui.block(copy, date, 0);
            Ui.weight(card, copy, 8);
            card.addView(Ui.icon(context, R.drawable.ic_ui_chevron, R.color.ui_muted, 22));
            Ui.block(content, card, 8);
        }
        footer.setVisibility(View.VISIBLE);
        Ui.block(footer, Ui.button(context, R.string.record_add, R.drawable.ic_ui_add, R.color.ui_mint,
                R.color.ui_green, () -> editRecord(null)), 0);
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
        Context context = requireContext();
        LinearLayout form = Ui.column(context);
        Ui.pad(form, 16, 12);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(form);
        AlertDialog modal = Ui.formDialog(scroll);
        modal.setOnDismissListener(ignored -> recordDialogOpen = false);
        Ui.dialogHeader(form, record == null ? R.string.record_add : R.string.record_edit, modal::dismiss);

        EditText exercise = formField(form, R.string.record_exercise, record == null ? "" : record.exercise());
        divider(form);
        TextView heading = Ui.text(context, getString(R.string.record_parameters), 15, R.color.ui_text, true);
        ViewCompat.setAccessibilityHeading(heading, true);
        Ui.block(form, heading, 8);
        LinearLayout rows = Ui.column(context);
        Ui.block(form, rows, 0);
        List<ParameterInputs> parameters = new ArrayList<>();
        if (record != null) {
            for (var param : record.parameters()) addParameter(rows, parameters, param.name(), param.value());
        }
        Ui.block(form, Ui.button(context, R.string.record_add_parameter, R.drawable.ic_ui_add,
                R.color.ui_card, R.color.ui_green, () -> addParameter(rows, parameters, "", "")), 0);
        divider(form);

        LinearLayout actions = Ui.row(context);
        Ui.weight(actions, Ui.button(context, R.string.cancel, 0, R.color.ui_lilac,
                R.color.ui_text, modal::dismiss), 8);
        MaterialButton save = Ui.button(context, R.string.save, 0, R.color.ui_mint, R.color.ui_green, () -> {});
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
        Ui.weight(actions, save, 0);
        Ui.block(form, actions, 0);

        form.setFocusableInTouchMode(true);
        form.requestFocus();
        Ui.showFormDialog(modal);
    }

    private void divider(LinearLayout parent) {
        View line = new View(parent.getContext());
        line.setBackgroundColor(Ui.color(parent.getContext(), R.color.ui_border));
        var params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(10);
        params.bottomMargin = dp(10);
        parent.addView(line, params);
    }

    private EditText formField(LinearLayout parent, int label, String initial) {
        Context context = parent.getContext();
        TextView caption = Ui.text(context, getString(label), 13, R.color.ui_muted, false);
        EditText field = new TextInputEditText(context);
        field.setId(View.generateViewId());
        caption.setLabelFor(field.getId());
        Ui.block(parent, caption, 4);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setText(initial);
        field.setTextSize(15);
        field.setTextColor(Ui.color(context, R.color.ui_text));
        field.setMinHeight(dp(48));
        field.setSelectAllOnFocus(true);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                Ui.surface(context, R.color.ui_neutral, 10, R.color.ui_green_border));
        background.addState(new int[]{}, Ui.surface(context, R.color.ui_neutral, 10, R.color.ui_border));
        field.setBackground(background);
        field.setBackgroundTintList(null);
        Ui.pad(field, 10, 8);
        Ui.block(parent, field, 0);
        return field;
    }

    private void addParameter(LinearLayout rows, List<ParameterInputs> inputs, String name, String value) {
        Context context = rows.getContext();
        LinearLayout pair = Ui.row(context);
        pair.setGravity(Gravity.BOTTOM);
        LinearLayout key = Ui.column(context);
        LinearLayout amount = Ui.column(context);
        var parameter = new ParameterInputs(formField(key, R.string.record_parameter_name, name),
                formField(amount, R.string.record_parameter_value, value));
        inputs.add(parameter);
        Ui.weight(pair, key, 8);
        Ui.weight(pair, amount, 4);
        pair.addView(Ui.iconButton(context, R.drawable.ic_ui_delete, R.string.delete,
                R.color.ui_muted, R.color.ui_card, () -> {
                    inputs.remove(parameter);
                    rows.removeView(pair);
                }));
        Ui.block(rows, pair, 8);
    }

    private void addAvatar(AccountViewModel.ProfileCard profile) {
        content.addView(avatar(profile), new LinearLayout.LayoutParams(dp(84), dp(84)));
    }

    private ImageView avatar(AccountViewModel.ProfileCard profile) {
        Context context = requireContext();
        ImageView avatar = new ImageView(context);
        avatar.setContentDescription(getString(R.string.profile_avatar));
        Ui.paper(avatar, R.color.ui_neutral, 14, 0);
        avatar.setClipToOutline(true);
        if (profile.avatar() == null) {
            avatar.setImageResource(R.drawable.ic_ui_person);
            avatar.setImageTintList(ColorStateList.valueOf(Ui.color(context, R.color.ui_muted)));
            avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Ui.pad(avatar, 20, 20);
        } else {
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setImageBitmap(profile.avatar());
        }
        return avatar;
    }

    private void addCoachBar() {
        coachBar = Ui.row(requireContext());
        Ui.block(content, coachBar, 10);
    }

    private void renderCoach(CoachViewModel.State state) {
        if (coachBar != null) CoachBar.render(coachBar, state, coach::dismiss, this::openCoachSelection);
        if (coachName != null) {
            coachName.setText(state == null || state.selected() == null ? getString(R.string.ui_profile_no_coach)
                    : getString(R.string.profile_trainer, state.selected().name()));
        }
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
