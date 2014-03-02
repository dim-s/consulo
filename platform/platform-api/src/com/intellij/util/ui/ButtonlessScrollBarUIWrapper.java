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
package com.intellij.util.ui;

import javax.accessibility.Accessible;
import javax.swing.*;
import java.awt.*;

/**
 * @author VISTALL
 * @since 02.03.14
 */
public class ButtonlessScrollBarUIWrapper extends ButtonlessScrollBarUI {
  private ButtonlessScrollBarUI myDelegate;

  public ButtonlessScrollBarUIWrapper(JScrollBar component) {
    myDelegate = ButtonlessScrollBarUI.createUIImpl(component);
  }

  @Override
  public void layoutContainer(Container scrollbarContainer) {
    myDelegate.layoutContainer(scrollbarContainer);
  }

  @Override
  protected ModelListener createModelListener() {
    return myDelegate.createModelListener();
  }

  @Override
  public int getDecrementButtonHeight() {
    return myDelegate.getDecrementButtonHeight();
  }

  @Override
  public int getIncrementButtonHeight() {
    return myDelegate.getIncrementButtonHeight();
  }

  @Override
  public void installUI(JComponent c) {
    myDelegate.installUI(c);
  }

  @Override
  public void installDefaults() {
    myDelegate.installDefaults();
  }

  @Override
  public void installListeners() {
    myDelegate.installListeners();
  }

  @Override
  public Rectangle getThumbBounds() {
    return myDelegate.getThumbBounds();
  }

  @Override
  public void uninstallListeners() {
    myDelegate.uninstallListeners();
  }

  @Override
  public void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
    myDelegate.paintTrack(g, c, bounds);
  }

  @Override
  public Dimension getMinimumThumbSize() {
    return myDelegate.getMinimumThumbSize();
  }

  @Override
  public int getThickness() {
    return myDelegate.getThickness();
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return myDelegate.getMaximumSize(c);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return myDelegate.getMinimumSize(c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return myDelegate.getPreferredSize(c);
  }

  @Override
  public void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    myDelegate.paintThumb(g, c, thumbBounds);
  }

  @Override
  public boolean getSupportsAbsolutePositioning() {
    return myDelegate.getSupportsAbsolutePositioning();
  }

  @Override
  public int adjustThumbWidth(int width) {
    return myDelegate.adjustThumbWidth(width);
  }

  @Override
  public Color adjustColor(Color c) {
    return myDelegate.adjustColor(c);
  }

  @Override
  public JButton createIncreaseButton(int orientation) {
    return myDelegate.createIncreaseButton(orientation);
  }

  @Override
  public void uninstallUI(JComponent c) {
    myDelegate.uninstallUI(c);
  }

  @Override
  public boolean isThumbRollover() {
    return myDelegate.isThumbRollover();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    myDelegate.paint(g, c);
  }

  @Override
  public void addLayoutComponent(String name, Component child) {
    myDelegate.addLayoutComponent(name, child);
  }

  @Override
  public void removeLayoutComponent(Component child) {
    myDelegate.removeLayoutComponent(child);
  }

  @Override
  public Dimension preferredLayoutSize(Container scrollbarContainer) {
    return myDelegate.preferredLayoutSize(scrollbarContainer);
  }

  @Override
  public Dimension minimumLayoutSize(Container scrollbarContainer) {
    return myDelegate.minimumLayoutSize(scrollbarContainer);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    myDelegate.update(g, c);
  }

  @Override
  public boolean contains(JComponent c, int x, int y) {
    return myDelegate.contains(c, x, y);
  }

  @Override
  public int getBaseline(JComponent c, int width, int height) {
    return myDelegate.getBaseline(c, width, height);
  }

  @Override
  public Component.BaselineResizeBehavior getBaselineResizeBehavior(JComponent c) {
    return myDelegate.getBaselineResizeBehavior(c);
  }

  @Override
  public int getAccessibleChildrenCount(JComponent c) {
    return myDelegate.getAccessibleChildrenCount(c);
  }

  @Override
  public Accessible getAccessibleChild(JComponent c, int i) {
    return myDelegate.getAccessibleChild(c, i);
  }

  @Override
  public JButton createDecreaseButton(int orientation) {
    return myDelegate.createDecreaseButton(orientation);
  }

  public ButtonlessScrollBarUI getDelegate() {
    return myDelegate;
  }
}
