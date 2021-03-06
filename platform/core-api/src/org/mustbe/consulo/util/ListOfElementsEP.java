package org.mustbe.consulo.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author VISTALL
 * @since 0:44/07.11.13
 */
public class ListOfElementsEP {
  public static final ExtensionPointName<ListOfElementsEP> EP_NAME = ExtensionPointName.create("com.intellij.listOfElements");

  public static final String LIST_VARIABLE_START = "@@";

  @Attribute("name")
  public String myName;

  @Property(surroundWithTag = false) @AbstractCollection(surroundWithTag = false)
  public ElementEP[] defaultValues;

  public static MultiMap<String, String> ourCache;

  public static String[] getValuesOfVariableIfFound(String text) {
    if (StringUtil.isEmptyOrSpaces(text)) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    if (text.startsWith(LIST_VARIABLE_START)) {
      Collection<String> valuesOf = getValuesOf(text);
      if (valuesOf.isEmpty()) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      return ArrayUtil.toStringArray(valuesOf);
    }
    else {
      List<String> split = StringUtil.split(text, ",");
      return ArrayUtil.toStringArray(split);
    }
  }

  @NotNull
  public static Collection<String> getValuesOf(String name) {
    if (ourCache == null) {
      ourCache = new MultiMap<String, String>();
      for (ListOfElementsEP listOfElementsEP : EP_NAME.getExtensions()) {
        if (listOfElementsEP.defaultValues != null) {
          for (ElementEP s : listOfElementsEP.defaultValues) {
            ourCache.putValue(LIST_VARIABLE_START + listOfElementsEP.myName, s.myValue);
          }
        }

        for (AddToElementEP addElementEP : AddToElementEP.EP_NAME.getExtensions()) {
          if (addElementEP.myName.equals(listOfElementsEP.myName)) {
            ourCache.putValue(LIST_VARIABLE_START + listOfElementsEP.myName, addElementEP.myValue);
          }
        }
      }
    }
    return ourCache.get(name);
  }
}
