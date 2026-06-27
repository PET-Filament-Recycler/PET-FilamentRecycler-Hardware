package com.petfilament.recycler;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class LogActivity extends AppCompatActivity {

    private RecyclerView recyclerViewLogs;
    private TextView textViewEmpty;
    private MaterialButton buttonRefresh;
    private MaterialButton buttonClear;
    private LogsAdapter logsAdapter;
    private DatabaseHelper databaseHelper;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_log);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar), (view, windowInsets) -> {
            Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(0, statusBars.top, 0, 0);
            return windowInsets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_control);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_language) {
                LocaleHelper.toggleLanguage(this);
                recreate();
                return true;
            }
            return false;
        });

        recyclerViewLogs = findViewById(R.id.recyclerview_logs);
        textViewEmpty = findViewById(R.id.textview_logs_empty);
        buttonRefresh = findViewById(R.id.button_refresh_logs);
        buttonClear = findViewById(R.id.button_clear_logs);

        databaseHelper = DatabaseHelper.getInstance(this);

        recyclerViewLogs.setLayoutManager(new LinearLayoutManager(this));
        logsAdapter = new LogsAdapter();
        recyclerViewLogs.setAdapter(logsAdapter);

        buttonRefresh.setOnClickListener(v -> {
            reloadLogs();
            Toast.makeText(this, R.string.log_refreshed, Toast.LENGTH_SHORT).show();
        });

        buttonClear.setOnClickListener(v -> confirmClearLogs());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadLogs();
    }

    private void reloadLogs() {
        logsAdapter.replaceAll(databaseHelper.getAllLogs());
        boolean hasLogs = logsAdapter.getItemCount() > 0;
        textViewEmpty.setVisibility(hasLogs ? View.GONE : View.VISIBLE);
        recyclerViewLogs.setVisibility(hasLogs ? View.VISIBLE : View.GONE);
        buttonClear.setEnabled(hasLogs);
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.log_clear)
                .setMessage(R.string.log_clear_confirm)
                .setPositiveButton(R.string.log_clear, (dialog, which) -> {
                    databaseHelper.clearAllLogs();
                    reloadLogs();
                    Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}