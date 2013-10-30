/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public final class ASTFactory {
  private static final CharTable WHITESPACES = new CharTableImpl();

  // factory methods
  @NotNull
  public static LazyParseableElement lazy(@NotNull ILazyParseableElementType type,
                                          @NotNull LanguageVersion<?> languageVersion,
                                          @NotNull CharSequence text) {
    LazyParseableElement lazy = ASTLazyFactory.EP.getValue(type).createLazy(type, languageVersion, text);
    lazy.putUserData(LanguageVersion.KEY, languageVersion);
    return lazy;
  }

  @NotNull
  public static CompositeElement composite(@NotNull IElementType type, @NotNull LanguageVersion<?> languageVersion) {
    CompositeElement composite = ASTCompositeFactory.EP.getValue(type).createComposite(type, languageVersion);
    composite.putUserData(LanguageVersion.KEY, languageVersion);
    return composite;
  }

  @NotNull
  public static LeafElement leaf(@NotNull IElementType type, @NotNull LanguageVersion<?> languageVersion, @NotNull CharSequence text) {
    LeafElement leaf = ASTLeafFactory.EP.getValue(type).createLeaf(type, languageVersion, text);
    leaf.putUserData(LanguageVersion.KEY, languageVersion);
    return leaf;
  }

  @NotNull
  public static LeafElement whitespace(final CharSequence text) {
    return whitespace(text, Language.ANY_VERSION);
  }

  @NotNull
  public static LeafElement whitespace(final CharSequence text, LanguageVersion languageVersion) {
    final PsiWhiteSpaceImpl w = new PsiWhiteSpaceImpl(WHITESPACES.intern(text), languageVersion);
    CodeEditUtil.setNodeGenerated(w, true);
    return w;
  }
}
