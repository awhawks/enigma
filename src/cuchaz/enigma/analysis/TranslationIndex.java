/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.enigma.analysis;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import cuchaz.enigma.mapping.ArgumentEntry;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.JavassistUtil;
import cuchaz.enigma.mapping.Translator;

public class TranslationIndex implements Serializable {
	
	private static final long serialVersionUID = 738687982126844179L;
	
	private Map<ClassEntry,ClassEntry> m_superclasses;
	private Multimap<ClassEntry,FieldEntry> m_fieldEntries;
	private Multimap<ClassEntry,BehaviorEntry> m_behaviorEntries;
	
	public TranslationIndex() {
		m_superclasses = Maps.newHashMap();
		m_fieldEntries = HashMultimap.create();
		m_behaviorEntries = HashMultimap.create();
	}
	
	public TranslationIndex(TranslationIndex other, Translator translator) {
		
		// translate the superclasses
		m_superclasses = Maps.newHashMap();
		for (Map.Entry<ClassEntry,ClassEntry> mapEntry : other.m_superclasses.entrySet()) {
			m_superclasses.put(
				translator.translateEntry(mapEntry.getKey()),
				translator.translateEntry(mapEntry.getValue())
			);
		}
		
		// translate the fields
		m_fieldEntries = HashMultimap.create();
		for (Map.Entry<ClassEntry,FieldEntry> mapEntry : other.m_fieldEntries.entries()) {
			m_fieldEntries.put(
				translator.translateEntry(mapEntry.getKey()),
				translator.translateEntry(mapEntry.getValue())
			);
		}
		
		m_behaviorEntries = HashMultimap.create();
		for (Map.Entry<ClassEntry,BehaviorEntry> mapEntry : other.m_behaviorEntries.entries()) {
			m_behaviorEntries.put(
				translator.translateEntry(mapEntry.getKey()),
				translator.translateEntry(mapEntry.getValue())
			);
		}
	}
	
	public void indexClass(CtClass c) {
		
		ClassEntry classEntry = JavassistUtil.getClassEntry(c);
		
		// add the superclass
		ClassEntry superclassEntry = JavassistUtil.getSuperclassEntry(c);
		if (!isJre(classEntry) && !isJre(superclassEntry)) {
			m_superclasses.put(classEntry, superclassEntry);
		}
		
		// add fields
		for (CtField field : c.getDeclaredFields()) {
			FieldEntry fieldEntry = JavassistUtil.getFieldEntry(field);
			m_fieldEntries.put(fieldEntry.getClassEntry(), fieldEntry);
		}
		
		// add behaviors
		for (CtBehavior behavior : c.getDeclaredBehaviors()) {
			BehaviorEntry behaviorEntry = JavassistUtil.getBehaviorEntry(behavior);
			m_behaviorEntries.put(behaviorEntry.getClassEntry(), behaviorEntry);
		}
	}
	
	public void renameClasses(Map<String,String> renames) {
		EntryRenamer.renameClassesInMap(renames, m_superclasses);
		EntryRenamer.renameClassesInMultimap(renames, m_fieldEntries);
		EntryRenamer.renameClassesInMultimap(renames, m_behaviorEntries);
	}
	
	public ClassEntry getSuperclass(ClassEntry classEntry) {
		return m_superclasses.get(classEntry);
	}
	
	public List<ClassEntry> getAncestry(ClassEntry classEntry) {
		List<ClassEntry> ancestors = Lists.newArrayList();
		while (classEntry != null) {
			classEntry = getSuperclass(classEntry);
			if (classEntry != null) {
				ancestors.add(classEntry);
			}
		}
		return ancestors;
	}
	
	public List<ClassEntry> getSubclass(ClassEntry classEntry) {
		// linear search is fast enough for now
		List<ClassEntry> subclasses = Lists.newArrayList();
		for (Map.Entry<ClassEntry,ClassEntry> entry : m_superclasses.entrySet()) {
			ClassEntry subclass = entry.getKey();
			ClassEntry superclass = entry.getValue();
			if (classEntry.equals(superclass)) {
				subclasses.add(subclass);
			}
		}
		return subclasses;
	}
	
	public void getSubclassesRecursively(Set<ClassEntry> out, ClassEntry classEntry) {
		for (ClassEntry subclassEntry : getSubclass(classEntry)) {
			out.add(subclassEntry);
			getSubclassesRecursively(out, subclassEntry);
		}
	}
	
	public void getSubclassNamesRecursively(Set<String> out, ClassEntry classEntry) {
		for (ClassEntry subclassEntry : getSubclass(classEntry)) {
			out.add(subclassEntry.getName());
			getSubclassNamesRecursively(out, subclassEntry);
		}
	}
	
	public boolean entryExists(Entry entry) {
		if (entry instanceof FieldEntry) {
			return fieldExists((FieldEntry)entry);
		} else if (entry instanceof BehaviorEntry) {
			return behaviorExists((BehaviorEntry)entry);
		} else if (entry instanceof ArgumentEntry) {
			return behaviorExists(((ArgumentEntry)entry).getBehaviorEntry());
		}
		throw new IllegalArgumentException("Cannot check existence for " + entry.getClass());
	}
	
	public boolean fieldExists(FieldEntry fieldEntry) {
		return m_fieldEntries.containsEntry(fieldEntry.getClassEntry(), fieldEntry);
	}
	
	public boolean behaviorExists(BehaviorEntry behaviorEntry) {
		return m_behaviorEntries.containsEntry(behaviorEntry.getClassEntry(), behaviorEntry);
	}
	
	public ClassEntry resolveEntryClass(Entry entry) {
		
		if (entry instanceof ClassEntry) {
			return (ClassEntry)entry;
		}
		
		// this entry could refer to a method on a class where the method is not actually implemented
		// travel up the inheritance tree to find the closest implementation
		while (!entryExists(entry)) {
			
			// is there a parent class?
			ClassEntry superclassEntry = getSuperclass(entry.getClassEntry());
			if (superclassEntry == null) {
				// this is probably a method from a class in a library
				// we can't trace the implementation up any higher unless we index the library
				return null;
			}
			
			// move up to the parent class
			entry = entry.cloneToNewClass(superclassEntry);
		}
		return entry.getClassEntry();
	}
	
	private boolean isJre(ClassEntry classEntry) {
		return classEntry.getPackageName().startsWith("java") || classEntry.getPackageName().startsWith("javax");
	}
}
