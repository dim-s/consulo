/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl.storage;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ClassPathStorageUtil {
  @NonNls public static final String DEFAULT_STORAGE = "default";
  @NonNls public static final String CLASSPATH_OPTION = "classpath";

  public static boolean isDefaultStorage(@NotNull Module module) {
    final String storageType = getStorageType(module);
    return storageType.equals(DEFAULT_STORAGE);
  }

  @NotNull
  public static String getStorageType(@NotNull Module module) {
    final String id = module.getOptionValue(CLASSPATH_OPTION);
    return id == null ? DEFAULT_STORAGE : id;
  }
}
