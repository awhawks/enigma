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
package cuchaz.enigma.convert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.convert.ClassNamer.SidedClassNamer;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ClassMapping;
import cuchaz.enigma.mapping.ClassNameReplacer;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.FieldMapping;
import cuchaz.enigma.mapping.Mappings;
import cuchaz.enigma.mapping.MappingsChecker;
import cuchaz.enigma.mapping.MemberMapping;
import cuchaz.enigma.mapping.MethodEntry;
import cuchaz.enigma.mapping.MethodMapping;
import cuchaz.enigma.mapping.Signature;
import cuchaz.enigma.mapping.Type;

public class MappingsConverter {
	
	public static ClassMatches computeClassMatches(JarFile sourceJar, JarFile destJar, Mappings mappings) {
		
		// index jars
		System.out.println("Indexing source jar...");
		JarIndex sourceIndex = new JarIndex();
		sourceIndex.indexJar(sourceJar, false);
		System.out.println("Indexing dest jar...");
		JarIndex destIndex = new JarIndex();
		destIndex.indexJar(destJar, false);
		
		// compute the matching
		ClassMatching matching = computeMatching(sourceJar, sourceIndex, destJar, destIndex, null);
		return new ClassMatches(matching.matches());
	}
	
	public static ClassMatching computeMatching(JarFile sourceJar, JarIndex sourceIndex, JarFile destJar, JarIndex destIndex, BiMap<ClassEntry,ClassEntry> knownMatches) {
		
		System.out.println("Iteratively matching classes");
		
		ClassMatching lastMatching = null;
		int round = 0;
		SidedClassNamer sourceNamer = null;
		SidedClassNamer destNamer = null;
		for (boolean useReferences : Arrays.asList(false, true)) {
			
			int numUniqueMatchesLastTime = 0;
			if (lastMatching != null) {
				numUniqueMatchesLastTime = lastMatching.uniqueMatches().size();
			}
			
			while (true) {
				
				System.out.println("Round " + (++round) + "...");
				
				// init the matching with identity settings
				ClassMatching matching = new ClassMatching(
					new ClassIdentifier(sourceJar, sourceIndex, sourceNamer, useReferences),
					new ClassIdentifier(destJar, destIndex, destNamer, useReferences)
				);
				
				if (knownMatches != null) {
					matching.addKnownMatches(knownMatches);
				}
				
				if (lastMatching == null) {
					// search all classes
					matching.match(sourceIndex.getObfClassEntries(), destIndex.getObfClassEntries());
				} else {
					// we already know about these matches from last time
					matching.addKnownMatches(lastMatching.uniqueMatches());
					
					// search unmatched and ambiguously-matched classes
					matching.match(lastMatching.unmatchedSourceClasses(), lastMatching.unmatchedDestClasses());
					for (ClassMatch match : lastMatching.ambiguousMatches()) {
						matching.match(match.sourceClasses, match.destClasses);
					}
				}
				System.out.println(matching);
				BiMap<ClassEntry,ClassEntry> uniqueMatches = matching.uniqueMatches();
				
				// did we match anything new this time?
				if (uniqueMatches.size() > numUniqueMatchesLastTime) {
					numUniqueMatchesLastTime = uniqueMatches.size();
					lastMatching = matching;
				} else {
					break;
				}
				
				// update the namers
				ClassNamer namer = new ClassNamer(uniqueMatches);
				sourceNamer = namer.getSourceNamer();
				destNamer = namer.getDestNamer();
			}
		}
		
		return lastMatching;
	}
	
	public static Mappings newMappings(ClassMatches matches, Mappings oldMappings, Deobfuscator sourceDeobfuscator, Deobfuscator destDeobfuscator) {
		
		// sort the unique matches by size of inner class chain
		Multimap<Integer,java.util.Map.Entry<ClassEntry,ClassEntry>> matchesByDestChainSize = HashMultimap.create();
		for (java.util.Map.Entry<ClassEntry,ClassEntry> match : matches.getUniqueMatches().entrySet()) {
			int chainSize = destDeobfuscator.getJarIndex().getObfClassChain(match.getValue()).size();
			matchesByDestChainSize.put(chainSize, match);
		}
		
		// build the mappings (in order of small-to-large inner chains)
		Mappings newMappings = new Mappings();
		List<Integer> chainSizes = Lists.newArrayList(matchesByDestChainSize.keySet());
		Collections.sort(chainSizes);
		for (int chainSize : chainSizes) {
			for (java.util.Map.Entry<ClassEntry,ClassEntry> match : matchesByDestChainSize.get(chainSize)) {
				
				// get class info
				ClassEntry obfSourceClassEntry = match.getKey();
				ClassEntry obfDestClassEntry = match.getValue();
				List<ClassEntry> destClassChain = destDeobfuscator.getJarIndex().getObfClassChain(obfDestClassEntry);
				
				ClassMapping sourceMapping = sourceDeobfuscator.getMappings().getClassByObf(obfSourceClassEntry);
				if (sourceMapping == null) {
					// if this class was never deobfuscated, don't try to match it
					continue;
				}
				
				// find out where to make the dest class mapping
				if (destClassChain.size() == 1) {
					// not an inner class, add directly to mappings
					newMappings.addClassMapping(migrateClassMapping(obfDestClassEntry, sourceMapping, matches, false));
				} else {
					// inner class, find the outer class mapping
					ClassMapping destMapping = null;
					for (int i=0; i<destClassChain.size()-1; i++) {
						ClassEntry destChainClassEntry = destClassChain.get(i);
						if (destMapping == null) {
							destMapping = newMappings.getClassByObf(destChainClassEntry);
							if (destMapping == null) {
								destMapping = new ClassMapping(destChainClassEntry.getName());
								newMappings.addClassMapping(destMapping);
							}
						} else {
							destMapping = destMapping.getInnerClassByObf(destChainClassEntry.getInnerClassName());
							if (destMapping == null) {
								destMapping = new ClassMapping(destChainClassEntry.getName());
								destMapping.addInnerClassMapping(destMapping);
							}
						}
					}
					destMapping.addInnerClassMapping(migrateClassMapping(obfDestClassEntry, sourceMapping, matches, true));
				}
			}
		}
		return newMappings;
	}
	
	private static ClassMapping migrateClassMapping(ClassEntry newObfClass, ClassMapping mapping, final ClassMatches matches, boolean useSimpleName) {
		
		ClassNameReplacer replacer = new ClassNameReplacer() {
			@Override
			public String replace(String className) {
				ClassEntry newClassEntry = matches.getUniqueMatches().get(new ClassEntry(className));
				if (newClassEntry != null) {
					return newClassEntry.getName();
				}
				return null;
			}
		};
		
		ClassMapping newMapping;
		String deobfName = mapping.getDeobfName();
		if (deobfName != null) {
			if (useSimpleName) {
				deobfName = new ClassEntry(deobfName).getSimpleName();
			}
			newMapping = new ClassMapping(newObfClass.getName(), deobfName);
		} else {
			newMapping = new ClassMapping(newObfClass.getName());
		}
		
		// copy fields
		for (FieldMapping fieldMapping : mapping.fields()) {
			// TODO: map field obf names too...
			newMapping.addFieldMapping(new FieldMapping(fieldMapping, replacer));
		}
		
		// copy methods
		for (MethodMapping methodMapping : mapping.methods()) {
			// TODO: map method obf names too...
			newMapping.addMethodMapping(new MethodMapping(methodMapping, replacer));
		}
		
		return newMapping;
	}

	public static void convertMappings(Mappings mappings, BiMap<ClassEntry,ClassEntry> changes) {
		
		// sort the changes so classes are renamed in the correct order
		// ie. if we have the mappings a->b, b->c, we have to apply b->c before a->b
		LinkedHashMap<ClassEntry,ClassEntry> sortedChanges = Maps.newLinkedHashMap();
		int numChangesLeft = changes.size();
		while (!changes.isEmpty()) {
			Iterator<Map.Entry<ClassEntry,ClassEntry>> iter = changes.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<ClassEntry,ClassEntry> change = iter.next();
				if (changes.containsKey(change.getValue())) {
					sortedChanges.put(change.getKey(), change.getValue());
					iter.remove();
				}
			}
			
			// did we remove any changes?
			if (numChangesLeft - changes.size() > 0) {
				// keep going
				numChangesLeft = changes.size();
			} else {
				// can't sort anymore. There must be a loop
				break;
			}
		}
		if (!changes.isEmpty()) {
			throw new Error("Unable to sort class changes! There must be a cycle.");
		}
		
		// convert the mappings in the correct class order
		for (Map.Entry<ClassEntry,ClassEntry> entry : sortedChanges.entrySet()) {
			mappings.renameObfClass(entry.getKey().getName(), entry.getValue().getName());
		}
	}
	
	public static interface Doer<T extends Entry> {
		Collection<T> getDroppedEntries(MappingsChecker checker);
		Collection<T> getObfEntries(JarIndex jarIndex);
		Collection<? extends MemberMapping<T>> getMappings(ClassMapping destClassMapping);
		Set<T> filterEntries(Collection<T> obfEntries, T obfSourceEntry, ClassMatches classMatches);
		void setMemberObfName(ClassMapping classMapping, MemberMapping<T> memberMapping, String newObfName);
	}
	
	public static Doer<FieldEntry> getFieldDoer() {
		return new Doer<FieldEntry>() {

			@Override
			public Collection<FieldEntry> getDroppedEntries(MappingsChecker checker) {
				return checker.getDroppedFieldMappings().keySet();
			}

			@Override
			public Collection<FieldEntry> getObfEntries(JarIndex jarIndex) {
				return jarIndex.getObfFieldEntries();
			}

			@Override
			public Collection<? extends MemberMapping<FieldEntry>> getMappings(ClassMapping destClassMapping) {
				return (Collection<? extends MemberMapping<FieldEntry>>)destClassMapping.fields();
			}

			@Override
			public Set<FieldEntry> filterEntries(Collection<FieldEntry> obfDestFields, FieldEntry obfSourceField, ClassMatches classMatches) {
				Set<FieldEntry> out = Sets.newHashSet();
				for (FieldEntry obfDestField : obfDestFields) {
					Type translatedDestType = translate(obfDestField.getType(), classMatches.getUniqueMatches().inverse());
					if (translatedDestType.equals(obfSourceField.getType())) {
						out.add(obfDestField);
					}
				}
				return out;
			}

			@Override
			public void setMemberObfName(ClassMapping classMapping, MemberMapping<FieldEntry> memberMapping, String newObfName) {
				FieldMapping fieldMapping = (FieldMapping)memberMapping;
				classMapping.setFieldObfName(fieldMapping.getObfName(), fieldMapping.getObfType(), newObfName);
			}
		};
	}
	
	public static Doer<BehaviorEntry> getMethodDoer() {
		return new Doer<BehaviorEntry>() {

			@Override
			public Collection<BehaviorEntry> getDroppedEntries(MappingsChecker checker) {
				return checker.getDroppedMethodMappings().keySet();
			}

			@Override
			public Collection<BehaviorEntry> getObfEntries(JarIndex jarIndex) {
				return jarIndex.getObfBehaviorEntries();
			}

			@Override
			public Collection<? extends MemberMapping<BehaviorEntry>> getMappings(ClassMapping destClassMapping) {
				return (Collection<? extends MemberMapping<BehaviorEntry>>)destClassMapping.methods();
			}

			@Override
			public Set<BehaviorEntry> filterEntries(Collection<BehaviorEntry> obfDestFields, BehaviorEntry obfSourceField, ClassMatches classMatches) {
				Set<BehaviorEntry> out = Sets.newHashSet();
				for (BehaviorEntry obfDestField : obfDestFields) {
					Signature translatedDestSignature = translate(obfDestField.getSignature(), classMatches.getUniqueMatches().inverse());
					if (translatedDestSignature == null && obfSourceField.getSignature() == null) {
						out.add(obfDestField);
					} else if (translatedDestSignature == null || obfSourceField.getSignature() == null) {
						// skip it
					} else if (translatedDestSignature.equals(obfSourceField.getSignature())) {
						out.add(obfDestField);
					}
				}
				return out;
			}

			@Override
			public void setMemberObfName(ClassMapping classMapping, MemberMapping<BehaviorEntry> memberMapping, String newObfName) {
				MethodMapping methodMapping = (MethodMapping)memberMapping;
				classMapping.setMethodObfName(methodMapping.getObfName(), methodMapping.getObfSignature(), newObfName);
			}
		};
	}
	
	public static <T extends Entry> MemberMatches<T> computeMemberMatches(Deobfuscator destDeobfuscator, Mappings destMappings, ClassMatches classMatches, Doer<T> doer) {
		
		MemberMatches<T> memberMatches = new MemberMatches<T>();
		
		// unmatched source fields are easy
		MappingsChecker checker = new MappingsChecker(destDeobfuscator.getJarIndex());
		checker.dropBrokenMappings(destMappings);
		for (T destObfEntry : doer.getDroppedEntries(checker)) {
			T srcObfEntry = translate(destObfEntry, classMatches.getUniqueMatches().inverse());
			memberMatches.addUnmatchedSourceEntry(srcObfEntry);
		}
		
		// get matched fields (anything that's left after the checks/drops is matched(
		for (ClassMapping classMapping : destMappings.classes()) {
			collectMatchedFields(memberMatches, classMapping, classMatches, doer);
		}
		
		// get unmatched dest fields
		for (T destEntry : doer.getObfEntries(destDeobfuscator.getJarIndex())) {
			if (!memberMatches.isMatchedDestEntry(destEntry)) {
				memberMatches.addUnmatchedDestEntry(destEntry);
			}
		}

		System.out.println("Automatching " + memberMatches.getUnmatchedSourceEntries().size() + " unmatched source entries...");
		
		// go through the unmatched source fields and try to pick out the easy matches
		for (ClassEntry obfSourceClass : Lists.newArrayList(memberMatches.getSourceClassesWithUnmatchedEntries())) {
			for (T obfSourceEntry : Lists.newArrayList(memberMatches.getUnmatchedSourceEntries(obfSourceClass))) {
				
				// get the possible dest matches
				ClassEntry obfDestClass = classMatches.getUniqueMatches().get(obfSourceClass);
				
				// filter by type/signature
				Set<T> obfDestEntries = doer.filterEntries(memberMatches.getUnmatchedDestEntries(obfDestClass), obfSourceEntry, classMatches);
				
				if (obfDestEntries.size() == 1) {
					// make the easy match
					memberMatches.makeMatch(obfSourceEntry, obfDestEntries.iterator().next());
				} else if (obfDestEntries.isEmpty()) {
					// no match is possible =(
					memberMatches.makeSourceUnmatchable(obfSourceEntry);
				}
			}
		}
		
		System.out.println(String.format("Ended up with %d ambiguous and %d unmatchable source entries",
			memberMatches.getUnmatchedSourceEntries().size(),
			memberMatches.getUnmatchableSourceEntries().size()
		));
		
		return memberMatches;
	}
	
	private static <T extends Entry> void collectMatchedFields(MemberMatches<T> memberMatches, ClassMapping destClassMapping, ClassMatches classMatches, Doer<T> doer) {
		
		// get the fields for this class
		for (MemberMapping<T> destEntryMapping : doer.getMappings(destClassMapping)) {
			T destObfField = destEntryMapping.getObfEntry(destClassMapping.getObfEntry());
			T srcObfField = translate(destObfField, classMatches.getUniqueMatches().inverse());
			memberMatches.addMatch(srcObfField, destObfField);
		}
		
		// recurse
		for (ClassMapping destInnerClassMapping : destClassMapping.innerClasses()) {
			collectMatchedFields(memberMatches, destInnerClassMapping, classMatches, doer);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends Entry> T translate(T in, BiMap<ClassEntry,ClassEntry> map) {
		if (in instanceof FieldEntry) {
			return (T)new FieldEntry(
				map.get(in.getClassEntry()),
				in.getName(),
				translate(((FieldEntry)in).getType(), map)
			);
		} else if (in instanceof MethodEntry) {
			return (T)new MethodEntry(
				map.get(in.getClassEntry()),
				in.getName(),
				translate(((MethodEntry)in).getSignature(), map)
			);
		} else if (in instanceof ConstructorEntry) {
			return (T)new ConstructorEntry(
				map.get(in.getClassEntry()),
				translate(((ConstructorEntry)in).getSignature(), map)
			);
		}
		throw new Error("Unhandled entry type: " + in.getClass());
	}

	private static Type translate(Type type, final BiMap<ClassEntry,ClassEntry> map) {
		return new Type(type, new ClassNameReplacer() {
			@Override
			public String replace(String inClassName) {
				ClassEntry outClassEntry = map.get(new ClassEntry(inClassName));
				if (outClassEntry == null) {
					return null;
				}
				return outClassEntry.getName();
			}
		});
	}
	
	private static Signature translate(Signature signature, final BiMap<ClassEntry,ClassEntry> map) {
		if (signature == null) {
			return null;
		}
		return new Signature(signature, new ClassNameReplacer() {
			@Override
			public String replace(String inClassName) {
				ClassEntry outClassEntry = map.get(new ClassEntry(inClassName));
				if (outClassEntry == null) {
					return null;
				}
				return outClassEntry.getName();
			}
		});
	}

	public static <T extends Entry> void applyMemberMatches(Mappings mappings, MemberMatches<T> memberMatches, Doer<T> doer) {
		for (ClassMapping classMapping : mappings.classes()) {
			applyMemberMatches(classMapping, memberMatches, doer);
		}
	}
	
	private static <T extends Entry> void applyMemberMatches(ClassMapping classMapping, MemberMatches<T> memberMatches, Doer<T> doer) {
		ClassEntry classEntry = classMapping.getObfEntry();
		
		// apply to this class
		// TODO: need to sort renames so they happen in the right order!!
		for (MemberMapping<T> memberMapping : Lists.newArrayList(doer.getMappings(classMapping))) {
			T obfSourceEntry = memberMapping.getObfEntry(classEntry);
			T obfDestEntry = memberMatches.matches().get(obfSourceEntry);
			if (obfDestEntry != null) {
				doer.setMemberObfName(classMapping, memberMapping, obfDestEntry.getName());
			}
		}
		
		// recurse
		for (ClassMapping innerClassMapping : classMapping.innerClasses()) {
			applyMemberMatches(innerClassMapping, memberMatches, doer);
		}
	}
}
