package edu.buffalo.cse.jive.model.lib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class Tools
{
  public static <T> ArrayList<T> newArrayList()
  {
    return new ArrayList<T>();
  }

  public static <T> List<T> newArrayList(final int capacity)
  {
    return new ArrayList<T>(capacity);
  }

  public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMap()
  {
    return new ConcurrentHashMap<K, V>();
  }

  public static <K, V> ConcurrentHashMap<K, V> newConcurrentHashMap(final int size)
  {
    return new ConcurrentHashMap<K, V>(size);
  }

  public static <K> Set<K> newConcurrentSet(final int size)
  {
    return Collections.newSetFromMap(new ConcurrentHashMap<K, Boolean>(size));
  }

  public static <K, V> HashMap<K, V> newHashMap()
  {
    return new HashMap<K, V>();
  }

  public static <K, V> HashMap<K, V> newHashMap(final int size)
  {
    return new HashMap<K, V>(size);
  }

  public static <T> Set<T> newHashSet()
  {
    return Collections.newSetFromMap(new HashMap<T, Boolean>());
  }

  public static <T> Set<T> newHashSet(final int size)
  {
    return Collections.newSetFromMap(new HashMap<T, Boolean>(size));
  }

  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap()
  {
    return new LinkedHashMap<K, V>();
  }

  public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(final int size)
  {
    return new LinkedHashMap<K, V>(size);
  }

  // public static <T> LinkedList<T> newLinkedList() {
  //
  // return new LinkedList<T>();
  // }
  public static <T> Set<T> newLinkedHashSet()
  {
    return new LinkedHashSet<T>();
  }

  public static <K, V> SortedMap<K, V> newSortedMap()
  {
    return new TreeMap<K, V>();
  }

  public static <M> Set<M> newSortedSet()
  {
    return new TreeSet<M>();
  }

  public static <T> List<T> newUnmodifiableList(final Collection<? extends T> source)
  {
    final List<T> list = Tools.newArrayList();
    list.addAll(source);
    return Collections.unmodifiableList(list);
  }
  
  /**
   * Counts the number of times a character appears in a string. 
   * @param s	-the string to search
   * @param c	-the character to look for
   * @return	number of times c occurs in s
   */
  public static int countOccurrences(String s, char c)
  {
      int count = 0;
      for (int i=0; i < s.length(); i++)
      {
          if (s.charAt(i) == c)
          {
               count++;
          }
      }
      return count;
  }

  /**
   * Combines two {@link String} arrays, allowing only one entry in case of duplicates. 
   * @param s1
   * @param s2
   * @return
   */
public static String[] combineStringArrays(String[] s1,	String[] s2) {
	String[] temp	= new String[s1.length+s2.length];
	int emptyIndex	= 0;
	
	for (int i = 0; i < s1.length; i++) {
		temp[i] = s1[i];
		emptyIndex++;
	}
	
	for (int i = 0; i < s2.length; i++) {
		boolean found = false;
		for (int j = 0; j < emptyIndex; j++) {
			if (s2[i] == null) {
				found = true;
			} else if(s2[i].equals(temp[j])){
				found = true;
			}
		}
		if(!found){
			temp[emptyIndex] = s2[i];
			emptyIndex++;
		}
	}
	String[] result = new String[emptyIndex];
	
	for (int i = 0; i < result.length; i++) {
		result[i] = temp[i];
	}
	return result;
}
}
