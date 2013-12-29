package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.packageView.PackageViewNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 17:30/28.12.13
 */
public interface PackageViewHelper {
  ExtensionPointName<PackageViewHelper> EP_NAME = ExtensionPointName.create("com.intellij.packageViewHelper");

  @NotNull
  String getPresentationName();

  boolean hasNodesFromModule(@NotNull Module module);

  @NotNull
  PackageViewNode[] getNodesFromModule(@NotNull Module module);
}
