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
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class StandardFileSystems {
  public static String FILE_PROTOCOL = "file";
  public static String FILE_PROTOCOL_PREFIX = "file://";
  @Deprecated
  public static String JAR_PROTOCOL = "jar";
  @Deprecated
  public static String JAR_PROTOCOL_PREFIX = "jar://";
  @Deprecated
  public static String JAR_SEPARATOR = "!/";
  public static String HTTP_PROTOCOL = "http";

  private static final NotNullLazyValue<IVirtualFileSystem> ourLocal = new NotNullLazyValue<IVirtualFileSystem>() {
    @NotNull
    @Override
    protected IVirtualFileSystem compute() {
      return VirtualFileManager.getInstance().getFileSystem(FILE_PROTOCOL);
    }
  };

  private static final NotNullLazyValue<ArchiveFileSystem> ourJar = new NotNullLazyValue<ArchiveFileSystem>() {
    @NotNull
    @Override
    protected ArchiveFileSystem compute() {
      return (ArchiveFileSystem)VirtualFileManager.getInstance().getFileSystem(JAR_PROTOCOL);
    }
  };

  public static IVirtualFileSystem local() {
    return ourLocal.getValue();
  }

  /**
   * @deprecated use JarArchiveFileType.INSTANCE.getFileSystem()
   * @return
   */
  @Deprecated
  public static ArchiveFileSystem jar() {
    return ourJar.getValue();
  }
}
