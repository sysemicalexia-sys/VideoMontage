package com.videomontage.ui.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.videomontage.app.R;
import com.videomontage.editor.model.Project;
import com.videomontage.editor.model.Timeline;
import com.videomontage.project.ProjectRepository;
import com.videomontage.ui.editor.EditorActivity;
import com.videomontage.utils.ViewFx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HomeActivity extends Activity {

    private ProjectRepository repository;
    private ProjectsAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toast.makeText(this, "VM 3.0-v2", Toast.LENGTH_LONG).show();
        repository = new ProjectRepository(this);

        GridView grid = findViewById(R.id.projectGrid);
        adapter = new ProjectsAdapter(getLayoutInflater(), new ProjectsAdapter.Callback() {
            @Override public void onOpen(Project project) {
                openEditor(project.id);
            }

            @Override public void onDelete(Project project) {
                confirmDelete(project);
            }
        });
        grid.setAdapter(adapter);

        View create = findViewById(R.id.createCard);
        create.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ViewFx.pressFeedback(v);
                createProject();
            }
        });

        ViewFx.fadeSlideIn(findViewById(R.id.headline), 60);
        ViewFx.fadeSlideIn(create, 140);
        ViewFx.fadeSlideIn(grid, 220);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        io.execute(new Runnable() {
            @Override public void run() {
                final List<Project> projects = repository.list();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (adapter != null) adapter.submit(projects);
                        TextView empty = findViewById(R.id.emptyHint);
                        if (empty != null) empty.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void createProject() {
        Project project = new Project("Untitled Project", Timeline.empty());
        repository.save(project);
        openEditor(project.id);
    }

    private void openEditor(String projectId) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
        startActivity(intent);
        overridePendingTransition(R.anim.enter_slide_up, R.anim.exit_fade);
    }

    private void confirmDelete(final Project project) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_project_title)
                .setMessage(getString(R.string.delete_project_message, project.name))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        repository.delete(project.id);
                        reload();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
