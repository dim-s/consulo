/*
 * Copyright 2013-2014 must-be.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.consulo.vfs.backgroundTask.tree;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.consulo.vfs.backgroundTask.BackgroundTaskByVfsChangeManager;
import org.consulo.vfs.backgroundTask.BackgroundTaskByVfsChangeTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @since 04.03.14
 */
public class BackgroundTaskPsiFileTreeNode extends PsiFileNode {
  public BackgroundTaskPsiFileTreeNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    VirtualFile ourVirtualFile = getVirtualFile();
    if(ourVirtualFile == null) {
      return super.getChildrenImpl();
    }
    BackgroundTaskByVfsChangeManager vfsChangeManager = BackgroundTaskByVfsChangeManager.getInstance(getProject());
    BackgroundTaskByVfsChangeTask task = vfsChangeManager.getTask(ourVirtualFile);
    if(task == null) {
      return super.getChildrenImpl();
    }
    VirtualFile[] generatedFiles = task.getGeneratedFiles();
    if(generatedFiles.length == 0) {
      return super.getChildrenImpl();
    }
    PsiFile[] psiFiles = PsiUtilBase.virtualToPsiFiles(generatedFiles, myProject);
    List<AbstractTreeNode> newChildren = new ArrayList<AbstractTreeNode>(psiFiles.length);
    for (PsiFile psiFile : psiFiles) {
      newChildren.add(new PsiFileNode(getProject(), psiFile, getSettings()));
    }
    return newChildren;
  }
}
