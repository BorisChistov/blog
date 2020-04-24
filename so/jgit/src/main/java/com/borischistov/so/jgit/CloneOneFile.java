package com.borischistov.so.jgit;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.ZipFormat;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CloneOneFile {

    private final static Logger logger = LoggerFactory.getLogger(CloneOneFile.class);

    public static void main(String [] args) throws IOException, URISyntaxException, GitAPIException {
        var repository = "git://sourceware.org/git/glibc.git";
        var fileName = "README";

        // git command git archive --remote git://sourceware.org/git/glibc.git HEAD README

        ArchiveCommand.registerFormat("zip", new ZipFormat());
        var gitRep = new InMemoryRepository(new DfsRepositoryDescription());
        var git = new Git(gitRep);
        git.remoteAdd().setName("origin").setUri(new URIish(repository)).call();
        System.out.println("Before");
        var messages = git.fetch()
                .setThin(true)
                .setRefSpecs("HEAD:refs/heads/master")
                .setRemote("origin")
                .call().getMessages();
        System.out.println("Response: " + messages);
        logger.warn("Commit: {}, {}", gitRep.readOrigHead(), messages);

        try (
                var out  = Files.newOutputStream(Paths.get("export.zip"))
        ) {
//            git.archive()
//                    .setPaths(fileName)
//                    .setOutputStream(out)
//                    .setTree(gitRep.readOrigHead())
//                    .setFormat("zip")
//                    .call();
        } finally {
            ArchiveCommand.unregisterFormat("zip");
        }
        git.close();
        gitRep.close();
    }
}
