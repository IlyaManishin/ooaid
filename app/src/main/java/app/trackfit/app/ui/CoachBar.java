package app.trackfit.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import app.trackfit.app.R;
import app.trackfit.app.presentation.CoachViewModel;

final class CoachBar {
    private CoachBar() {}

    static void render(LinearLayout area, CoachViewModel.State state, Runnable dismiss, Runnable openCoaches) {
        area.removeAllViews();
        boolean selected = state != null && state.selected() != null;
        area.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (!selected) return;

        Context context = area.getContext();
        boolean hasLine = state.line() != null;
        Ui.pad(area, 10, 4);
        Ui.tap(area, R.color.ui_card, 18, R.color.ui_border, openCoaches);
        area.addView(CoachAvatar.create(context, state.selected(), 38, R.color.ui_violet, R.color.ui_lilac));
        LinearLayout copy = Ui.column(context);
        Ui.pad(copy, 10, 4);
        copy.addView(Ui.text(context, state.selected().name(), 14, R.color.ui_text, true));
        if (hasLine) copy.addView(Ui.text(context, state.line(), 13, R.color.ui_muted, false));
        Ui.weight(area, copy, 0);
        if (hasLine) {
            area.addView(Ui.iconButton(context, R.drawable.ic_ui_close, R.string.close,
                    R.color.ui_muted, R.color.ui_card, dismiss));
        } else {
            area.addView(Ui.icon(context, R.drawable.ic_ui_expand, R.color.ui_muted, 22));
        }
    }
}
