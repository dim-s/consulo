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
package com.intellij.ide.ui.laf.modernWhite;

import com.intellij.ide.IdeBundle;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 02.03.14
 */
public class ModernWhiteLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  public ModernWhiteLookAndFeelInfo(){
    super(IdeBundle.message("modern.white.intellij.look.and.feel"), ModernWhiteLaf.class.getName());
  }

  @Override
  public boolean equals(Object obj){
    return (obj instanceof ModernWhiteLookAndFeelInfo);
  }

  @Override
  public int hashCode(){
    return getName().hashCode();
  }
}
