package com.codecontext.core.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Mines Git commit history using Eclipse JGit.
 */
public class JGitHistoryProvider implements GitHistoryProvider {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Map<String, GitEvolutionMetric> extractMetrics(Path repoRoot, int daysWindow) {
        if (repoRoot == null) return Map.of();

        File gitDir = findGitDir(repoRoot.toFile());
        if (gitDir == null || !gitDir.exists()) {
            return Map.of();
        }

        Map<String, FileStats> statsMap = new HashMap<>();
        Instant cutoff = Instant.now().minus(daysWindow, ChronoUnit.DAYS);

        try (Repository repository = new FileRepositoryBuilder().setGitDir(gitDir).readEnvironment().findGitDir().build();
             Git git = new Git(repository);
             RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);

            ObjectId head = repository.resolve("HEAD");
            if (head == null) {
                return Map.of();
            }

            revWalk.markStart(revWalk.parseCommit(head));

            for (RevCommit commit : revWalk) {
                Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
                if (commitTime.isBefore(cutoff)) {
                    break;
                }

                String author = commit.getAuthorIdent().getName();
                if (author == null || author.isBlank()) {
                    author = commit.getAuthorIdent().getEmailAddress();
                }
                if (author == null) author = "unknown";

                if (commit.getParentCount() > 0) {
                    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
                    CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                    CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                    try (ObjectReader reader = repository.newObjectReader()) {
                        oldTreeIter.reset(reader, parent.getTree().getId());
                        newTreeIter.reset(reader, commit.getTree().getId());
                    }

                    List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
                    for (DiffEntry diff : diffs) {
                        String filePath = diff.getNewPath();
                        if (filePath == null || filePath.equals("/dev/null")) {
                            filePath = diff.getOldPath();
                        }
                        if (filePath == null) continue;

                        filePath = filePath.replace('\\', '/');
                        FileStats stats = statsMap.computeIfAbsent(filePath, k -> new FileStats());
                        stats.commitCount++;
                        stats.authors.merge(author, 1, Integer::sum);

                        try {
                            for (var edit : diffFormatter.toFileHeader(diff).toEditList()) {
                                stats.linesAdded += (edit.getEndB() - edit.getBeginB());
                                stats.linesDeleted += (edit.getEndA() - edit.getBeginA());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    // Initial commit: walk all files in tree
                    try (TreeWalk treeWalk = new TreeWalk(repository)) {
                        treeWalk.addTree(commit.getTree());
                        treeWalk.setRecursive(true);
                        while (treeWalk.next()) {
                            String filePath = treeWalk.getPathString().replace('\\', '/');
                            FileStats stats = statsMap.computeIfAbsent(filePath, k -> new FileStats());
                            stats.commitCount++;
                            stats.authors.merge(author, 1, Integer::sum);
                            stats.linesAdded += 10;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return Map.of();
        }

        Map<String, GitEvolutionMetric> result = new HashMap<>();
        for (Map.Entry<String, FileStats> entry : statsMap.entrySet()) {
            String path = entry.getKey();
            FileStats s = entry.getValue();

            double busFactorRisk = calculateBusFactorRisk(s.authors);

            result.put(path, new GitEvolutionMetric(
                    path,
                    s.commitCount,
                    s.linesAdded,
                    s.linesDeleted,
                    s.authors.size(),
                    busFactorRisk
            ));
        }

        return Collections.unmodifiableMap(result);
    }

    private double calculateBusFactorRisk(Map<String, Integer> authorCounts) {
        if (authorCounts.isEmpty()) return 0.0;
        if (authorCounts.size() == 1) return 1.0;

        int total = authorCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return 0.0;

        double entropy = 0.0;
        for (int count : authorCounts.values()) {
            double p = (double) count / total;
            if (p > 0.0) {
                entropy -= (p * (Math.log(p) / Math.log(2.0)));
            }
        }

        double maxEntropy = Math.log(authorCounts.size()) / Math.log(2.0);
        if (maxEntropy <= 1e-9) return 1.0;

        double normalizedEntropy = entropy / maxEntropy;
        return Math.max(0.0, Math.min(1.0, 1.0 - normalizedEntropy));
    }

    private File findGitDir(File start) {
        File current = start;
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists()) {
                return gitDir;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static class FileStats {
        int commitCount = 0;
        int linesAdded = 0;
        int linesDeleted = 0;
        Map<String, Integer> authors = new HashMap<>();
    }
}
