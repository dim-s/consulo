/*
 * Copyright 2013 must-be.org
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
package com.intellij.ide.projectView.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.packageView.PackageViewNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderScopes;

/**
 * @author VISTALL
 * @since 17:35/28.12.13
 */
public class ContentRootPackageViewHelper implements PackageViewHelper {
  @NotNull
  @Override
  public String getPresentationName() {
    return IdeBundle.message("title.packages");
  }

  @Override
  public boolean hasNodesFromModule(@NotNull Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager.getContentFolderFiles(ContentFolderScopes.all(false)).length > 0;
  }

  @NotNull
  @Override
  public PackageViewNode[] getNodesFromModule(@NotNull Module module) {
    return new PackageViewNode[0];
  }
}
