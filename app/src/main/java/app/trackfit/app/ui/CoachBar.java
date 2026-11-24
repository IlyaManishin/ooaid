package app.trackfit.app.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.trackfit.app.R;
import app.trackfit.app.presentation.CoachViewModel;

final class CoachBar {
    private CoachBar() {}

    static void render(LinearLayout area, CoachViewModel.State state, Runnable dismiss) {
        area.removeAllViews();
        if (state == null || state.selected() == null) return;
        Context context = area.getContext();
        area.setOrientation(LinearLayout.HORIZONTAL);
        area.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(context);
        avatar.setImageResource(R.drawable.ic_tab_coach);
        int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
        area.addView(avatar, new LinearLayout.LayoutParams(size, size));
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(state.selected().name());
        name.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        copy.addView(name);
        if (state.line() != null) {
            TextView line = new TextView(context);
            line.setText(state.line());
            line.setMinHeight(size);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setOnClickListener(view -> dismiss.run());
            copy.addView(line);
        }
        area.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }
}
