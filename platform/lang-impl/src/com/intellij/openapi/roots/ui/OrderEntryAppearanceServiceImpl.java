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
package com.intellij.openapi.roots.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import org.consulo.module.extension.ModuleExtensionProviderEP;
import org.consulo.sdk.SdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class OrderEntryAppearanceServiceImpl extends OrderEntryAppearanceService {
  private static final String NO_SDK = ProjectBundle.message("sdk.missing.item");

  @NotNull
  @Override
  public CellAppearanceEx forOrderEntry(Project project, @NotNull final OrderEntry orderEntry, final boolean selected) {
    if(orderEntry instanceof ModuleExtensionWithSdkOrderEntry) {
      ModuleExtensionWithSdkOrderEntry sdkLibraryEntry = (ModuleExtensionWithSdkOrderEntry)orderEntry;
      Sdk sdk = sdkLibraryEntry.getSdk();
      if (!orderEntry.isValid() || sdk == null) {
        final String oldSdkName = sdkLibraryEntry.getSdkName();
        final ModuleExtensionProviderEP provider = ModuleExtensionProviderEP.findProviderEP(sdkLibraryEntry.getModuleExtensionId());
        return FileAppearanceService.getInstance().forInvalidUrl(oldSdkName != null ? oldSdkName : provider == null ? NO_SDK : provider.getName());
      }
      return forSdk(sdk, false, selected, true);
    }
    else if (!orderEntry.isValid()) {
      return FileAppearanceService.getInstance().forInvalidUrl(orderEntry.getPresentableName());
    }
    else if (orderEntry instanceof LibraryOrderEntry) {
      LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
      if (!libraryOrderEntry.isValid()) { //library can be removed
        return FileAppearanceService.getInstance().forInvalidUrl(orderEntry.getPresentableName());
      }
      Library library = libraryOrderEntry.getLibrary();
      assert library != null : libraryOrderEntry;
      return forLibrary(project, library, !((LibraryEx)library).getInvalidRootUrls(OrderRootType.CLASSES).isEmpty());
    }
    else if (orderEntry.isSynthetic()) {
      String presentableName = orderEntry.getPresentableName();
      Icon icon = orderEntry instanceof ModuleSourceOrderEntry ? sourceFolderIcon(false) : null;
      return new SimpleTextCellAppearance(presentableName, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
    else if (orderEntry instanceof ModuleOrderEntry) {
      return SimpleTextCellAppearance.regular(orderEntry.getPresentableName(), AllIcons.Nodes.Module);
    }
    else {
      return CompositeAppearance.single(orderEntry.getPresentableName());
    }
  }

  @NotNull
  @Override
  public CellAppearanceEx forLibrary(Project project, @NotNull final Library library, final boolean hasInvalidRoots) {
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(project).getContext();
    final Icon icon = LibraryPresentationManager.getInstance().getCustomIcon(library, context);

    final String name = library.getName();
    if (name != null) {
      return normalOrRedWaved(name, (icon != null ? icon :  AllIcons.Nodes.PpLib), hasInvalidRoots);
    }

    final String[] files = library.getUrls(OrderRootType.CLASSES);
    if (files.length == 0) {
      return SimpleTextCellAppearance.invalid(ProjectBundle.message("library.empty.library.item"),  AllIcons.Nodes.PpLib);
    }
    else if (files.length == 1) {
      return forVirtualFilePointer(new LightFilePointer(files[0]));
    }

    final String url = StringUtil.trimEnd(files[0], ArchiveFileSystem.ARCHIVE_SEPARATOR);
    return SimpleTextCellAppearance.regular(PathUtil.getFileName(url),  AllIcons.Nodes.PpLib);
  }

  @NotNull
  @Override
  public CellAppearanceEx forSdk(@Nullable final Sdk jdk, final boolean isInComboBox, final boolean selected, final boolean showVersion) {
    if (jdk == null) {
      return FileAppearanceService.getInstance().forInvalidUrl(NO_SDK);
    }

    String name = jdk.getName();
    CompositeAppearance appearance = new CompositeAppearance();
    SdkType sdkType = (SdkType)jdk.getSdkType();
    appearance.setIcon(SdkUtil.getIcon(jdk));
    SimpleTextAttributes attributes = getTextAttributes(sdkType.sdkHasValidPath(jdk), selected);
    CompositeAppearance.DequeEnd ending = appearance.getEnding();
    ending.addText(name, attributes);

    if (showVersion) {
      String versionString = jdk.getVersionString();
      if (versionString != null && !versionString.equals(name)) {
        SimpleTextAttributes textAttributes = isInComboBox && !selected ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES :
                                              SystemInfo.isMac && selected ? new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, 
                                                                                                      Color.WHITE): SimpleTextAttributes.GRAY_ATTRIBUTES;
        ending.addComment(versionString, textAttributes);
      }
    }

    return ending.getAppearance();
  }

  private static SimpleTextAttributes getTextAttributes(final boolean valid, final boolean selected) {
    if (!valid) {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else {
      return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    }
  }

  @NotNull
  @Override
  public CellAppearanceEx forContentFolder(@NotNull final ContentFolder folder) {
    return formatRelativePath(folder, folder.getType().getChildDirectoryIcon(null));
  }

  @NotNull
  @Override
  public CellAppearanceEx forModule(@NotNull final Module module) {
    return SimpleTextCellAppearance.regular(module.getName(), AllIcons.Nodes.Module);
  }

  @NotNull
  private static Icon sourceFolderIcon(final boolean testSource) {
    return testSource ? AllIcons.Nodes.TestPackage : AllIcons.Nodes.Package;
  }

  @NotNull
  private static CellAppearanceEx normalOrRedWaved(@NotNull final String text, @Nullable final Icon icon, final boolean waved) {
    return waved ? new SimpleTextCellAppearance(text, icon, new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, null, JBColor.RED))
                 : SimpleTextCellAppearance.regular(text, icon);
  }

  @NotNull
  private static CellAppearanceEx forVirtualFilePointer(@NotNull final LightFilePointer filePointer) {
    final VirtualFile file = filePointer.getFile();
    return file != null ? FileAppearanceService.getInstance().forVirtualFile(file)
                        : FileAppearanceService.getInstance().forInvalidUrl(filePointer.getPresentableUrl());
  }

  @NotNull
  private static CellAppearanceEx formatRelativePath(@NotNull final ContentFolder folder, @NotNull final Icon icon) {
    LightFilePointer folderFile = new LightFilePointer(folder.getUrl());
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(folder.getContentEntry().getUrl());
    if (file == null) return FileAppearanceService.getInstance().forInvalidUrl(folderFile.getPresentableUrl());

    String contentPath = file.getPath();
    String relativePath;
    SimpleTextAttributes textAttributes;
    VirtualFile folderFileFile = folderFile.getFile();
    if (folderFileFile == null) {
      String absolutePath = folderFile.getPresentableUrl();
      relativePath = absolutePath.startsWith(contentPath) ? absolutePath.substring(contentPath.length()) : absolutePath;
      textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else {
      relativePath = VfsUtilCore.getRelativePath(folderFileFile, file, File.separatorChar);
      textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    relativePath = StringUtil.isEmpty(relativePath) ? "." + File.separatorChar : relativePath;
    return new SimpleTextCellAppearance(relativePath, icon, textAttributes);
  }
}
