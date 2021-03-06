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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.consulo.lombok.annotations.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
@Logger
abstract class LibraryOrderEntryBaseImpl extends OrderEntryBaseImpl implements LibraryOrSdkOrderEntry {
  @NotNull
  protected DependencyScope myScope = DependencyScope.COMPILE;

  LibraryOrderEntryBaseImpl(@NotNull RootModelImpl rootModel) {
    super(rootModel);
  }

  protected final void init() {

  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    return getRootFiles(type);
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType type) {
    LOGGER.assertTrue(!getRootModel().getModule().isDisposed());
    return getRootUrls(type);
  }

  @Override
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider != null ? rootProvider.getFiles(type) : VirtualFile.EMPTY_ARRAY;
  }

  @Nullable
  protected abstract RootProvider getRootProvider();

  @Override
  @NotNull
  public String[] getRootUrls(@NotNull OrderRootType type) {
    RootProvider rootProvider = getRootProvider();
    return rootProvider == null ? ArrayUtil.EMPTY_STRING_ARRAY : rootProvider.getUrls(type);
  }

  @Override
  @NotNull
  public final Module getOwnerModule() {
    return getRootModel().getModule();
  }

  @Override
  public void dispose() {
    super.dispose();
  }
}
