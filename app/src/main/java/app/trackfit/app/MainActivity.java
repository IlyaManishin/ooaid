package app.trackfit.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import app.trackfit.app.databinding.ActivityMainBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public final class MainActivity extends AppCompatActivity {
    private NavController navController;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigation;

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {});

    public void showCoachSelection() {
        bottomNavigation.setSelectedItemId(R.id.coach_tab);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.coach_card_screen) {
            navController.popBackStack(R.id.coach_screen, false);
        }
    }

    public void offerNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        var preferences = getSharedPreferences("trackfit_permissions", MODE_PRIVATE);
        if (preferences.getBoolean("notification_prompted", false)) return;
        preferences.edit().putBoolean("notification_prompted", true).apply();
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets safeArea = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(0, safeArea.top, 0, 0);
            binding.navHost.setPadding(safeArea.left, 0, safeArea.right, 0);
            return windowInsets;
        });

        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host);
        if (host == null) {
            throw new IllegalStateException("Missing navigation host");
        }
        navController = host.getNavController();
        bottomNavigation = binding.bottomNavigation;
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        binding.bottomNavigation.setOnItemReselectedListener(item -> {});
    }
}
