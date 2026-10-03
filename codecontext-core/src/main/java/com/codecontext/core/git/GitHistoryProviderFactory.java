package com.codecontext.core.git;

import java.io.File;
import java.nio.file.Path;

/**
 * Factory that detects presence of .git repository and chooses the appropriate provider.
 */
public final class GitHistoryProviderFactory {

    private GitHistoryProviderFactory() {}

    public static GitHistoryProvider createProvider(Path root) {
        if (root == null) return new NoOpGitHistoryProvider();
        File gitDir = new File(root.toFile(), ".git");
        if (gitDir.exists()) {
            return new JGitHistoryProvider();
        }
        return new NoOpGitHistoryProvider();
    }
}
