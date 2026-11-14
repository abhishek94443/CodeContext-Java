package com.codecontext.core.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Inspects Git repositories to identify modified files either in the uncommitted
 * working tree or between the current HEAD and a specified base ref/branch.
 */
public class GitDiffAnalyzer {

    public record DiffAnalysisResult(
            boolean isGitRepo,
            String baseRef,
            Set<String> modifiedFiles,
            String message
    ) {}

    public DiffAnalysisResult analyzeDiff(Path repoRoot, String baseRef) {
        if (repoRoot == null) {
            return new DiffAnalysisResult(false, baseRef, Set.of(), "Repository root path is null");
        }

        File gitDir = new File(repoRoot.toFile(), ".git");
        if (!gitDir.exists()) {
            return new DiffAnalysisResult(false, baseRef, Set.of(), "Not a Git repository (no .git found in " + repoRoot + ")");
        }

        Set<String> modified = new LinkedHashSet<>();

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .setWorkTree(repoRoot.toFile())
                .build();
             Git git = new Git(repository)) {

            if (baseRef != null && !baseRef.isBlank()) {
                // Diff against base commit
                ObjectId baseId = repository.resolve(baseRef);
                ObjectId headId = repository.resolve("HEAD");

                if (baseId != null && headId != null) {
                    try (RevWalk revWalk = new RevWalk(repository);
                         DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repository);
                        df.setDetectRenames(true);

                        RevCommit baseCommit = revWalk.parseCommit(baseId);
                        RevCommit headCommit = revWalk.parseCommit(headId);

                        CanonicalTreeParser oldTree = new CanonicalTreeParser();
                        CanonicalTreeParser newTree = new CanonicalTreeParser();
                        try (ObjectReader reader = repository.newObjectReader()) {
                            oldTree.reset(reader, baseCommit.getTree().getId());
                            newTree.reset(reader, headCommit.getTree().getId());
                        }

                        List<DiffEntry> diffs = df.scan(oldTree, newTree);
                        for (DiffEntry d : diffs) {
                            String p = d.getNewPath();
                            if (p == null || p.equals("/dev/null")) {
                                p = d.getOldPath();
                            }
                            if (p != null) {
                                modified.add(p.replace('\\', '/'));
                            }
                        }
                    }
                }
            }

            // Also check working tree changes (uncommitted / unstaged / staged)
            Status status = git.status().call();
            for (String f : status.getModified()) modified.add(f.replace('\\', '/'));
            for (String f : status.getChanged()) modified.add(f.replace('\\', '/'));
            for (String f : status.getAdded()) modified.add(f.replace('\\', '/'));
            for (String f : status.getUntracked()) modified.add(f.replace('\\', '/'));

            return new DiffAnalysisResult(true, baseRef, Collections.unmodifiableSet(modified), "Diff successfully extracted");

        } catch (Exception e) {
            return new DiffAnalysisResult(true, baseRef, Set.of(), "Failed to extract git diff: " + e.getMessage());
        }
    }
}
