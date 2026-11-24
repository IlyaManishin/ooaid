package app.trackfit.app.ui;

import android.view.LayoutInflater;
import android.widget.LinearLayout;
import app.trackfit.app.databinding.ViewScreenHeaderBinding;

final class ScreenHeader {
    private ScreenHeader() {}

    static void addTo(LinearLayout parent, CharSequence title, Runnable navigateBack) {
        var header = ViewScreenHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, true).getRoot();
        header.setTitle(title);
        header.setNavigationOnClickListener(view -> navigateBack.run());
    }
}
