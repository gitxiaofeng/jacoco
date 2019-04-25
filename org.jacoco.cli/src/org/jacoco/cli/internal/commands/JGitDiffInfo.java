/*******************************************************************************
 * Copyright (c) 2009, 2019 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    xiaofeng - initial implementation
 *
 *******************************************************************************/
package org.jacoco.cli.internal.commands;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.io.File;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.lib.ObjectId;

import org.omg.CORBA.Object;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.portable.OutputStream;

/**
 * Created by xiaofeng on 2019/4/10.
 * Step1: 先将修改的文件列出来
 * Step2: 找到修改文件中修改的方法
 */
public class JGitDiffInfo {
    private String URL;
    private String clientPath;

    public JGitDiffInfo(String URL) {
        this.URL = URL;
    }

    public List<String> getDiff(String featureBranch) throws Exception {
        File gitPath = new File(URL);
        Git git = Git.open(gitPath);
        Repository repository = git.getRepository();

        try {
            git.checkout()
                    .setCreateBranch(true)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                    .setStartPoint("origin/master")
                    .setName("master")
                    .call();
        } catch (Exception e) {
            System.out.println(e);
        }

        AbstractTreeIterator newTreeIter = prepareTreeParser(repository, "refs/heads/" + featureBranch);
        AbstractTreeIterator oldTreeIter = prepareTreeParser(repository, "refs/heads/master");

        ByteArrayOutputStream o = new ByteArrayOutputStream();;
        List<String> modifiedClass = new ArrayList<String>();
        // finally get the list of changed files
        List<DiffEntry> diffs= git.diff()
                .setNewTree(newTreeIter)
                .setOldTree(oldTreeIter)
                .setOutputStream(o)
                .call();

        for (DiffEntry entry : diffs) {
            if (entry.getChangeType() == DiffEntry.ChangeType.DELETE || (!entry.toString().contains(".java"))) {
                System.out.println("delete action or not java file: " + entry);
                continue;
            } else {
                String[] items = entry.toString().split("/");
                modifiedClass.add(items[items.length - 1].split(".java")[0]);
            }
//            System.out.println("o: " + o.toString()); o是git diff的输出
        }

        System.out.println("Done, filelist is: " + modifiedClass);

        return modifiedClass;
    }


    private static String deleteCRLFOnce(String input) {
        return input.replaceAll("((\r\n)|\n)[\\s\t ]*(\\1)+", "$1");
    }
    // 根据节点信息获取
    // usage： CanonicalTreeParser newTreeIter = commitTreeParser(repository, "HEAD" + "^{tree}");
    private CanonicalTreeParser commitTreeParser(Repository repository, String head) throws Exception {
        ObjectId headSnap = repository.resolve(head);
        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        ObjectReader reader = repository.newObjectReader();
        oldTreeIter.reset(reader, headSnap);

        return oldTreeIter;
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, String ref) throws IOException {
        Ref head = repository.exactRef(ref);
        RevWalk walk = new RevWalk(repository);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        RevTree tree = walk.parseTree(commit.getTree().getId());

        CanonicalTreeParser treeParse = new CanonicalTreeParser();
        ObjectReader reader = repository.newObjectReader();
        treeParse.reset(reader, tree.getId());
        walk.dispose();

        return treeParse;
    }
}
