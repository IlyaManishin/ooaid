package app.trackfit.app.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import app.trackfit.app.R;
import app.trackfit.app.databinding.FragmentTabBinding;

public final class TabFragment extends Fragment {
    public TabFragment() {
        super(R.layout.fragment_tab);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentTabBinding binding = FragmentTabBinding.bind(view);
        binding.title.setText(requireArguments().getInt("title"));
        ViewCompat.setAccessibilityHeading(binding.title, true);
    }
}
