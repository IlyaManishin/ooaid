package app.trackfit.app;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MainActivitySmokeTest {
    @Test
    public void startsWithHiltAndNavigatesBetweenAppScreens() throws InterruptedException {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            BottomNavigationView tabs = activity.findViewById(R.id.bottom_navigation);
            assertNotNull(tabs);
            waitFor(activity, activity.getString(R.string.program_no_active));

            tabs.setSelectedItemId(R.id.coach_tab);
            click(activity, "Chloe");
            waitFor(activity, activity.getString(R.string.coach_bio));
            click(activity, activity.getString(R.string.coach_choose));
            waitFor(activity, activity.getString(R.string.coach_selected, "Chloe"));

            tabs.setSelectedItemId(R.id.profile_tab);
            click(activity, activity.getString(R.string.profile_records));
            waitFor(activity, activity.getString(R.string.record_add));
            String back = activity.getString(R.string.nav_back);
            View navigationIcon = waitFor(activity, back);
            assertTrue(navigationIcon instanceof android.widget.ImageButton);
            assertTrue(navigationIcon.getWidth() < tabs.getWidth() / 2);
            navigationIcon.performClick();
            click(activity, activity.getString(R.string.profile_settings));
            waitFor(activity, activity.getString(R.string.profile_push));
            click(activity, activity.getString(R.string.tab_coach));
            waitFor(activity, activity.getString(R.string.coach_selected, "Chloe"));

            tabs.setSelectedItemId(R.id.profile_tab);
            waitFor(activity, activity.getString(R.string.profile_push));
            tabs.setSelectedItemId(R.id.progress_tab);
            waitFor(activity, activity.getString(R.string.tab_progress));
            tabs.setSelectedItemId(R.id.home_tab);
            waitFor(activity, activity.getString(R.string.program_no_active));
        }
    }

    @Test
    public void createsProgramAndCompletesCycleFromRealScreens() throws InterruptedException {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            click(activity, activity.getString(R.string.programs_title));
            click(activity, activity.getString(R.string.programs_add));
            waitFor(activity, activity.getString(R.string.program_add_work));
            inputs(activity.findViewById(R.id.nav_host)).get(0).setText("Cycle check");
            click(activity, activity.getString(R.string.program_add_work));
            waitFor(activity, activity.getString(R.string.day_settings));
            inputs(activity.findViewById(R.id.nav_host)).get(1).setText("30");
            click(activity, activity.getString(R.string.exercise_add));
            clickDialog(activity.getString(R.string.exercise_strength));
            var fields = inputs(activeDialog().getWindow().getDecorView());
            fields.get(0).setText("Squat");
            fields.get(1).setText("1");
            fields.get(2).setText("8");
            clickDialog(activity.getString(R.string.save));
            click(activity, activity.getString(R.string.done));
            click(activity, activity.getString(R.string.save));
            waitFor(activity, activity.getString(R.string.programs_description));
            click(activity, "Cycle check");
            clickDialog(activity.getString(R.string.program_start));
            click(activity, activity.getString(R.string.workout_start));
            click(activity, "Squat");
            String progress = activity.getString(R.string.workout_running, 1, 1);
            waitFor(activity, progress);
            click(activity, activity.getString(R.string.workout_finish));
            clickDialog(activity.getString(R.string.report_next_cycle));
            waitFor(activity, activity.getString(R.string.cycle_waiting));
        }
    }

    private static Dialog activeDialog() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static void clickDialog(String label) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
            Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null && dialog.isShowing()) {
                View result = findText(dialog.getWindow().getDecorView(), label);
                if (result != null) {
                    for (var parent = result.getParent(); parent != null; parent = parent.getParent()) {
                        if (parent instanceof AdapterView<?> items) {
                            int position = items.getPositionForView(result);
                            items.performItemClick(result, position, items.getItemIdAtPosition(position));
                            return;
                        }
                    }
                    clickAction(result);
                    return;
                }
            }
            Thread.sleep(20);
        }
        fail("Missing dialog action: " + label);
    }

    private static List<EditText> inputs(View view) {
        List<EditText> result = new ArrayList<>();
        if (view instanceof EditText field) result.add(field);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) result.addAll(inputs(group.getChildAt(i)));
        }
        return result;
    }

    private static void click(MainActivity activity, String label) throws InterruptedException {
        clickAction(waitFor(activity, label));
    }

    private static void clickAction(View view) {
        for (View current = view; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current.isClickable()) {
                current.performClick();
                return;
            }
        }
        fail("Missing clickable view for: " + view);
    }

    private static View waitFor(MainActivity activity, String label) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
            View content = activity.findViewById(R.id.nav_host);
            View result = findText(content, label);
            if (result != null) return result;
            Thread.sleep(20);
        }
        fail("Missing text: " + label);
        throw new AssertionError();
    }

    private static View findText(View view, String label) {
        if (view == null || !view.isShown()) return null;
        if (view instanceof TextView text && label.contentEquals(text.getText())) return view;
        if (view.getContentDescription() != null && label.contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }
}
