/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiDirectory;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderTypeProvider;

import javax.swing.*;

public class CreateDirectoryOrPackageAction extends AnAction implements DumbAware {
  private enum ChildType {
    Directory {
      @Override
      public String getName() {
        return IdeBundle.message("action.directory");
      }

      @Override
      public String getSeparator() {
        return "\\/";
      }
    },
    Package {
      @Override
      public String getName() {
        return IdeBundle.message("action.package");
      }

      @Override
      public String getSeparator() {
        return ".";
      }
    };

    public abstract String getName();

    public abstract String getSeparator();
  }

  public CreateDirectoryOrPackageAction() {
    super(IdeBundle.message("action.create.new.directory.or.package"), IdeBundle.message("action.create.new.directory.or.package"), null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    Project project = e.getData(CommonDataKeys.PROJECT);

    if (view == null || project == null) {
      return;
    }

    val directory = DirectoryChooserUtil.getOrChooseDirectory(view);

    if (directory == null) {
      return;
    }

    val info = getInfo(directory);

    val validator =
      new CreateDirectoryOrPackageHandler(project, directory, info.getThird() == ChildType.Directory, info.getThird().getSeparator());
    Messages.showInputDialog(project, IdeBundle.message("prompt.enter.new.name"), info.getThird().getName(), Messages.getQuestionIcon(), "",
                             validator);

    val result = validator.getCreatedElement();
    if (result != null) {
      view.selectElement(result);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (view == null) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
      return;
    }

    presentation.setVisible(true);
    presentation.setEnabled(true);

    // is more that one directories not show package support
    if (directories.length > 1) {
      presentation.setText(ChildType.Directory.getName());
      presentation.setIcon(AllIcons.Nodes.TreeClosed);
    }
    else {
      val info = getInfo(directories[0]);

      presentation.setText(info.getThird().getName());

      val first = info.getFirst();
      Icon childIcon;
      if (first == null) {
        childIcon = AllIcons.Nodes.TreeClosed;
      }
      else {
        childIcon = first.getChildPackageIcon() == null ? first.getChildDirectoryIcon() : first.getChildPackageIcon();
      }
      presentation.setIcon(childIcon);
    }
  }

  @NotNull
  private static Trinity<ContentFolderTypeProvider, PsiDirectory, ChildType> getInfo(PsiDirectory d) {
    val project = d.getProject();
    val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project);

    val contentFolderTypeForFile = projectFileIndex.getContentFolderTypeForFile(d.getVirtualFile());
    if (contentFolderTypeForFile != null) {
      val childPackageIcon = contentFolderTypeForFile.getChildPackageIcon();
      return Trinity.create(contentFolderTypeForFile, d, childPackageIcon != null ? ChildType.Package : ChildType.Directory);
    }

    return Trinity.create(null, d, ChildType.Directory);
  }
}
