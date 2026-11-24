package app.trackfit.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import app.trackfit.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

final class Ui {
    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int resource) {
        return ContextCompat.getColor(context, resource);
    }

    static GradientDrawable surface(Context context, int fill, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color(context, fill));
        shape.setCornerRadius(dp(context, radius));
        if (stroke != 0) shape.setStroke(dp(context, 1), color(context, stroke));
        return shape;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static void pad(View view, int horizontal, int vertical) {
        int x = dp(view.getContext(), horizontal);
        int y = dp(view.getContext(), vertical);
        view.setPaddingRelative(x, y, x, y);
    }

    static void block(LinearLayout parent, View view, int gap) {
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.bottomMargin = dp(parent.getContext(), gap);
        parent.addView(view, params);
    }

    static void weight(LinearLayout parent, View view, int gap) {
        var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        params.setMarginEnd(dp(parent.getContext(), gap));
        parent.addView(view, params);
    }

    static void inline(LinearLayout parent, View view, int gap) {
        var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginEnd(dp(parent.getContext(), gap));
        parent.addView(view, params);
    }

    static void paper(View view, int fill, int radius, int stroke) {
        view.setBackground(surface(view.getContext(), fill, radius, stroke));
    }

    static void tap(View view, int fill, int radius, int stroke, Runnable action) {
        Context context = view.getContext();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2453627B),
                surface(context, fill, radius, stroke), null));
        view.setFocusable(true);
        view.setOnClickListener(ignored -> action.run());
    }

    static TextView text(Context context, CharSequence value, int size, int ink, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(context, ink));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static void heading(LinearLayout parent, CharSequence value) {
        TextView title = text(parent.getContext(), value, 24, R.color.ui_text, true);
        ViewCompat.setAccessibilityHeading(title, true);
        block(parent, title, 8);
    }

    static ImageView icon(Context context, int resource, int hue, int size) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(resource);
        icon.setImageTintList(ColorStateList.valueOf(color(context, hue)));
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(context, size), dp(context, size)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return icon;
    }

    static ImageView badge(Context context, int resource, int hue, int fill, int size) {
        ImageView badge = icon(context, resource, hue, size);
        pad(badge, size / 4, size / 4);
        paper(badge, fill, 12, 0);
        return badge;
    }

    static ImageButton iconButton(Context context, int resource, int label, int hue, int fill, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(color(context, hue)));
        button.setContentDescription(context.getText(label));
        tap(button, fill, 14, 0, action);
        return button;
    }

    static MaterialButton button(Context context, int label, int icon, int fill, int ink, Runnable action) {
        MaterialButton button = new MaterialButton(context);
        ColorStateList tint = new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
                new int[] {color(context, R.color.ui_muted), color(context, ink)});
        button.setText(label);
        button.setTextColor(tint);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(dp(context, 14));
        button.setBackgroundTintList(ColorStateList.valueOf(color(context, fill)));
        if (icon != 0) {
            button.setIconResource(icon);
            button.setIconTint(tint);
            button.setIconSize(dp(context, 20));
            button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        }
        button.setOnClickListener(view -> action.run());
        return button;
    }

    static void brand(LinearLayout parent, Runnable settings) {
        Context context = parent.getContext();
        LinearLayout header = row(context);
        ImageView logo = icon(context, R.drawable.ic_ui_strength, R.color.ui_green, 28);
        logo.setRotation(-12);
        inline(header, logo, 9);
        weight(header, text(context, context.getText(R.string.app_name), 26, R.color.ui_text, true), 8);
        header.addView(iconButton(context, R.drawable.ic_ui_settings, R.string.profile_settings,
                R.color.ui_text, R.color.ui_card, settings));
        block(parent, header, 12);
    }

    static AlertDialog formDialog(View body) {
        Context context = body.getContext();
        return new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_TrackFit_Dialog)
                .setBackground(surface(context, R.color.ui_card, 22, R.color.ui_border))
                .setView(body)
                .create();
    }

    static void showFormDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            Context context = dialog.getContext();
            window.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels, dp(context, 460)),
                    WRAP_CONTENT);
        }
    }

    static void dialogHeader(LinearLayout parent, int title, Runnable close) {
        Context context = parent.getContext();
        LinearLayout header = row(context);
        header.addView(badge(context, R.drawable.ic_ui_strength, R.color.ui_violet, R.color.ui_lilac, 38));
        TextView label = text(context, context.getText(title), 18, R.color.ui_text, true);
        pad(label, 10, 0);
        ViewCompat.setAccessibilityHeading(label, true);
        weight(header, label, 0);
        header.addView(iconButton(context, R.drawable.ic_ui_close, R.string.close, R.color.ui_muted,
                R.color.ui_card, close));
        block(parent, header, 10);
    }
}
