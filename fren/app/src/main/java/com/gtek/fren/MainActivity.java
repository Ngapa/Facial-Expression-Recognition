package com.gtek.fren;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.gtek.fren.databinding.ActivityMainBinding;
import com.gtek.fren.ui.helper.EmotionBenchmark;
import com.gtek.fren.ui.helper.EmotionClassifier;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration mAppBarConfiguration;
    private EmotionClassifier emotionClassifier;
    private EmotionBenchmark emotionBenchmark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.gtek.fren.databinding.ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        // Inisialisasi EmotionClassifier
        emotionClassifier = new EmotionClassifier(this);
        emotionBenchmark = new EmotionBenchmark();

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_emotion_analysis, R.id.nav_about, R.id.nav_about_emotion, R.id.nav_privacy_policy)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.model_cnnresnet || id == R.id.model_kanresnet) {
            try {
                if (id == R.id.model_cnnresnet) {
                    emotionClassifier.switchModel(EmotionClassifier.MODEL_CNN_RESNET);
                } else {
                    emotionClassifier.switchModel(EmotionClassifier.MODEL_KAN_RESNET);
                }

                item.setChecked(true);

                String modelName = (id == R.id.model_cnnresnet) ? "CNN ResEmoteNet" : "KAN ResEmoteNet";
                Toast.makeText(this, "Switched to " + modelName, Toast.LENGTH_SHORT).show();

                invalidateOptionsMenu();

                if (emotionBenchmark != null) {
                    emotionBenchmark.reset();
                }

                //TODO
                //Clear resultList when uploading a new image

                return true;
            } catch (IOException e) {
                Log.e("MainActivity", "Error switching model: " + e.getMessage());
                Toast.makeText(this, "Error switching model", Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem selectModelItem = menu.findItem(R.id.action_select_model);
        if (selectModelItem != null && selectModelItem.hasSubMenu()) {
            Menu subMenu = selectModelItem.getSubMenu();
            for (int i = 0; i < subMenu.size(); i++) {
                MenuItem subMenuItem = subMenu.getItem(i);
                if (subMenuItem.getItemId() == R.id.model_cnnresnet) {
                    subMenuItem.setChecked(emotionClassifier.getCurrentModel().equals(EmotionClassifier.MODEL_CNN_RESNET));
                } else if (subMenuItem.getItemId() == R.id.model_kanresnet) {
                    subMenuItem.setChecked(emotionClassifier.getCurrentModel().equals(EmotionClassifier.MODEL_KAN_RESNET));
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}