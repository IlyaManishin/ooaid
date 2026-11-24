package app.trackfit.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import app.trackfit.app.R;
import app.trackfit.app.presentation.CoachViewModel;
import java.io.IOException;

final class CoachAvatar {
    private CoachAvatar() {}

    static ImageView create(Context context, CoachViewModel.Card coach, int size, int ink, int fill) {
        ImageView avatar = Ui.badge(context, R.drawable.ic_ui_person, ink, fill, size);
        String path = coach.avatar();
        if (path == null || path.isBlank()) return avatar;

        try (var stream = context.getAssets().open(path)) {
            Drawable photo = Drawable.createFromStream(stream, path);
            if (photo != null) {
                avatar.setImageDrawable(photo);
                avatar.setImageTintList(null);
                avatar.setPadding(0, 0, 0, 0);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                avatar.setClipToOutline(true);
            }
        } catch (IOException ignored) {
            // A missing optional image leaves the default silhouette visible.
        }
        return avatar;
    }
}
