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
package com.intellij.openapi.diff.impl;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.DiffViewerType;
import com.intellij.openapi.diff.impl.external.DiscloseMultiRequest;
import com.intellij.openapi.diff.impl.external.MultiLevelDiffTool;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/13/12
 * Time: 1:59 PM
 */
public class CompositeDiffPanel implements DiffViewer {
  private final static int ourBadHackMagicContentsNumber = 101;
  private final RunnerLayoutUi myUi;
  private final DiscloseMultiRequest myRequest;
  private final Window myWindow;
  private final Disposable myParentDisposable;
  private final Map<String, DiffViewer> myMap;

  public CompositeDiffPanel(Project project, final DiscloseMultiRequest request, final Window window, final Disposable parentDisposable) {
    myRequest = request;
    myWindow = window;
    myParentDisposable = parentDisposable;
    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Diff", "Diff", "Diff", project);
    myUi.getComponent().setBorder(null);
    myUi.getOptions().setMinimizeActionEnabled(false);
    //myUi.getOptions().setTopToolbar()
    myMap = new HashMap<String, DiffViewer>();
  }

  @Override
  public void setDiffRequest(DiffRequest request) {
    final Map<String, DiffRequest> requestMap = myRequest.discloseRequest(request);
    final HashMap<String, DiffViewer> copy = new HashMap<String, DiffViewer>(myMap);

    for (Map.Entry<String, DiffRequest> entry : requestMap.entrySet()) {
      final String key = entry.getKey();
      final DiffRequest diffRequest = entry.getValue();
      final DiffViewer viewer = copy.remove(key);
      if (viewer != null) {
        viewer.setDiffRequest(diffRequest);
      } else {
        final DiffViewer newViewer = myRequest.viewerForRequest(myWindow, myParentDisposable, key, diffRequest);
        myMap.put(key, newViewer);
        final Content content = myUi.createContent(key, newViewer.getComponent(), key, null, newViewer.getPreferredFocusedComponent());
        content.setCloseable(false);
        content.setPinned(true);
        content.setDisposer(myParentDisposable);
        myUi.addContent(content);
      }
    }
    final Content[] contents = myUi.getContentManager().getContents();
    for (String s : copy.keySet()) {
      myMap.remove(s);
      for (Content content : contents) {
        if (s.equals(content.getTabName())) {
          myUi.getContentManager().removeContent(content, false);
          break;
        }
      }
    }
  }

  @Override
  public JComponent getComponent() {
    return myUi.getComponent();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final Content[] contents = myUi.getContents();
    if (contents == null || contents.length == 0) return null;
    return contents[0].getPreferredFocusableComponent();
  }

  @Override
  public int getContentsNumber() {
    return ourBadHackMagicContentsNumber;
  }

  @Override
  public DiffViewerType getType() {
    return DiffViewerType.multiLayer;
  }
}