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
package com.intellij.openapi.roots.impl;

import com.google.common.base.Predicate;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderScopes;
import org.mustbe.consulo.roots.ContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ExcludedContentFolderTypeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class RootModelBase implements ModuleRootModel {
  @Override
  @NotNull
  public VirtualFile[] getContentRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (ContentEntry contentEntry : getContent()) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  @NotNull
  public String[] getContentRootUrls() {
    if (getContent().isEmpty()) return ArrayUtil.EMPTY_STRING_ARRAY;
    final ArrayList<String> result = new ArrayList<String>(getContent().size());

    for (ContentEntry contentEntry : getContent()) {
      result.add(contentEntry.getUrl());
    }

    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public String[] getExcludeRootUrls() {
    final List<String> result = new SmartList<String>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getFolderUrls(ContentFolderScopes.of(ExcludedContentFolderTypeProvider.getInstance())));
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeRoots() {
    final List<VirtualFile> result = new SmartList<VirtualFile>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getFolderFiles(ContentFolderScopes.of(ExcludedContentFolderTypeProvider.getInstance())));
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public String[] getContentFolderUrls(@NotNull Predicate<ContentFolderTypeProvider> predicate) {
    List<String> result = new SmartList<String>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getFolderUrls(predicate));
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  @Override
  public VirtualFile[] getContentFolderFiles(@NotNull Predicate<ContentFolderTypeProvider> predicate) {
    List<VirtualFile> result = new SmartList<VirtualFile>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getFolderFiles(predicate));
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public ContentFolder[] getContentFolders(@NotNull Predicate<ContentFolderTypeProvider> predicate) {
    List<ContentFolder> result = new SmartList<ContentFolder>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, contentEntry.getFolders(predicate));
    }
    return result.isEmpty() ? ContentFolder.EMPTY_ARRAY : result.toArray(new ContentFolder[result.size()]);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls() {
    return getSourceRootUrls(true);
  }

  @Override
  @NotNull
  public String[] getSourceRootUrls(boolean includingTests) {
    List<String> result = new SmartList<String>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, includingTests
                                 ? contentEntry.getFolderUrls(ContentFolderScopes.productionAndTest())
                                 : contentEntry.getFolderUrls(ContentFolderScopes.production()));
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots() {
    return getSourceRoots(true);
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceRoots(final boolean includingTests) {
    List<VirtualFile> result = new SmartList<VirtualFile>();
    for (ContentEntry contentEntry : getContent()) {
      Collections.addAll(result, includingTests
                                 ? contentEntry.getFolderFiles(ContentFolderScopes.productionAndTest())
                                 : contentEntry.getFolderFiles(ContentFolderScopes.production()));
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public ContentEntry[] getContentEntries() {
    final Collection<ContentEntry> content = getContent();
    return content.toArray(new ContentEntry[content.size()]);
  }

  protected abstract Collection<ContentEntry> getContent();

  @NotNull
  @Override
  public OrderEnumerator orderEntries() {
    return new ModuleOrderEnumerator(this, null);
  }

  @Override
  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (OrderEntry orderEntry : getOrderEntries()) {
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  @Override
  @NotNull
  public String[] getDependencyModuleNames() {
    List<String> result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries()
      .process(new CollectDependentModules(), new ArrayList<String>());
    return ArrayUtil.toStringArray(result);
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies() {
    return getModuleDependencies(true);
  }

  @Override
  @NotNull
  public Module[] getModuleDependencies(boolean includeTests) {
    final List<Module> result = new ArrayList<Module>();

    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        final DependencyScope scope = moduleOrderEntry.getScope();
        if (!includeTests && !scope.isForProductionCompile() && !scope.isForProductionRuntime()) {
          continue;
        }
        final Module module1 = moduleOrderEntry.getModule();
        if (module1 != null) {
          result.add(module1);
        }
      }
    }

    return result.isEmpty() ? Module.EMPTY_ARRAY : ContainerUtil.toArray(result, new Module[result.size()]);
  }

  private static class CollectDependentModules extends RootPolicy<List<String>> {
    @NotNull
    @Override
    public List<String> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, @NotNull List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }
}
