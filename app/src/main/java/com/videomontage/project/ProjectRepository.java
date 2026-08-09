package com.videomontage.project;

import android.content.Context;

import com.videomontage.editor.model.Project;
import com.videomontage.storage.StoragePaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** File-backed project storage: one .vmproj per project under the app's
 *  private directory. Writes are atomic (tmp + rename) — a killed app
 *  never leaves a half-written project. */
public final class ProjectRepository {

    private final File dir;

    public ProjectRepository(Context context) {
        dir = StoragePaths.projectsDir(context);
    }

    public List<Project> list() {
        File[] files = dir.listFiles();
        if (files == null) return Collections.<Project>emptyList();
        List<Project> out = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".vmproj")) continue;
            try {
                out.add(ProjectStore.fromJson(read(f)));
            } catch (RuntimeException ignored) {
                // Corrupt entries are skipped, not fatal.
            }
        }
        Collections.sort(out, new Comparator<Project>() {
            @Override public int compare(Project a, Project b) {
                return Long.compare(b.modifiedAtMs, a.modifiedAtMs);
            }
        });
        return out;
    }

    public Project load(String projectId) {
        File f = fileFor(projectId);
        if (!f.exists()) return null;
        try {
            return ProjectStore.fromJson(read(f));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void save(Project project) {
        File tmp = new File(dir, project.id + ".tmp");
        write(tmp, ProjectStore.toJson(project));
        File target = fileFor(project.id);
        if (!tmp.renameTo(target)) {
            write(target, ProjectStore.toJson(project));
            tmp.delete();
        }
    }

    public void delete(String projectId) {
        fileFor(projectId).delete();
    }

    private File fileFor(String projectId) {
        return new File(dir, projectId + ".vmproj");
    }

    private static String read(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            in.close();
            return new String(buf, 0, read, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + f, e);
        }
    }

    private static void write(File f, String content) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            out.close();
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + f, e);
        }
    }
}
