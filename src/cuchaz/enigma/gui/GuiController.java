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
package cuchaz.enigma.gui;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cuchaz.enigma.ClassFile;
import cuchaz.enigma.Deobfuscator;
import cuchaz.enigma.analysis.SourceIndex;
import cuchaz.enigma.analysis.Token;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.EntryPair;
import cuchaz.enigma.mapping.MappingsReader;
import cuchaz.enigma.mapping.MappingsWriter;
import cuchaz.enigma.mapping.TranslationDirection;
import cuchaz.enigma.mapping.Translator;

public class GuiController
{
	private Deobfuscator m_deobfuscator;
	private Gui m_gui;
	private SourceIndex m_index;
	private ClassFile m_currentFile;
	private boolean m_isDirty;
	
	public GuiController( Gui gui )
	{
		m_gui = gui;
		m_deobfuscator = null;
		m_index = null;
		m_currentFile = null;
		m_isDirty = false;
	}
	
	public boolean isDirty( )
	{
		return m_isDirty;
	}
	
	public void openJar( File file )
	throws IOException
	{
		m_deobfuscator = new Deobfuscator( file );
		m_gui.onOpenJar( m_deobfuscator.getJarName() );
		refreshClasses();
	}
	
	public void closeJar( )
	{
		m_deobfuscator = null;
		m_gui.onCloseJar();
	}
	
	public void openMappings( File file )
	throws IOException
	{
		FileReader in = new FileReader( file );
		m_deobfuscator.setMappings( new MappingsReader().read( in ) );
		in.close();
		m_isDirty = false;
		m_gui.setMappingsFile( file );
		refreshClasses();
		refreshOpenFiles();
	}

	public void saveMappings( File file )
	throws IOException
	{
		FileWriter out = new FileWriter( file );
		new MappingsWriter().write( out, m_deobfuscator.getMappings() );
		out.close();
		m_isDirty = false;
	}

	public void closeMappings( )
	{
		m_deobfuscator.setMappings( null );
		m_gui.setMappingsFile( null );
		refreshClasses();
		refreshOpenFiles();
	}
	
	public void deobfuscateClass( ClassFile classFile )
	{
		m_currentFile = classFile;
		deobfuscate( m_currentFile );
	}
	
	public Token getToken( int pos )
	{
		if( m_index == null )
		{
			return null;
		}
		
		return m_index.getToken( pos );
	}
	
	public EntryPair<Entry> getEntryPair( Token token )
	{
		if( m_index == null )
		{
			return null;
		}
		
		Entry deobfEntry = m_index.getEntry( token );
		return new EntryPair<Entry>( m_deobfuscator.obfuscateEntry( deobfEntry ), deobfEntry );
	}
	
	public boolean entryHasMapping( Entry deobfEntry )
	{
		return m_deobfuscator.hasMapping( m_deobfuscator.obfuscateEntry( deobfEntry ) );
	}
	
	public boolean entryIsObfuscatedIdenfitier( Entry deobfEntry )
	{
		return m_deobfuscator.entryIsObfuscatedIdenfitier( m_deobfuscator.obfuscateEntry( deobfEntry ) );
	}
	
	public ClassInheritanceTreeNode getClassInheritance( ClassEntry classEntry )
	{
		Translator deobfuscatingTranslator = m_deobfuscator.getTranslator( TranslationDirection.Deobfuscating );
		
		// create a node for this class
		ClassInheritanceTreeNode thisNode = new ClassInheritanceTreeNode( deobfuscatingTranslator, classEntry.getName() );
		
		// expand all children recursively
		thisNode.load( m_deobfuscator.getAncestries(), true );
		
		// get the ancestors too
		ClassInheritanceTreeNode node = thisNode;
		for( String superclassName : m_deobfuscator.getAncestries().getAncestry( classEntry.getName() ) )
		{
			// add the parent node
			ClassInheritanceTreeNode parentNode = new ClassInheritanceTreeNode( deobfuscatingTranslator, superclassName );
			parentNode.add( node );
			node = parentNode;
		}
		
		return thisNode;
	}
	
	public void rename( Entry obfsEntry, String newName, int lineNum )
	{
		m_deobfuscator.rename( obfsEntry, newName );
		m_isDirty = true;
		refreshClasses();
		refreshOpenFiles( lineNum );
	}
	
	private void refreshClasses( )
	{
		List<ClassFile> obfClasses = Lists.newArrayList();
		Map<ClassFile,String> deobfClasses = Maps.newHashMap();
		m_deobfuscator.getSeparatedClasses( obfClasses, deobfClasses );
		m_gui.setObfClasses( obfClasses );
		m_gui.setDeobfClasses( deobfClasses );
	}

	private void refreshOpenFiles( )
	{
		refreshOpenFiles( 0 );
	}
	
	private void refreshOpenFiles( int lineNum )
	{
		if( m_currentFile != null )
		{
			deobfuscate( m_currentFile, lineNum );
		}
	}

	private void deobfuscate( final ClassFile classFile )
	{
		deobfuscate( classFile, 0 );
	}
	
	private void deobfuscate( final ClassFile classFile, final int lineNum )
	{
		m_gui.setSource( "(deobfuscating...)" );
		
		// run the deobfuscator in a separate thread so we don't block the GUI event queue
		new Thread( )
		{
			@Override
			public void run( )
			{
				// decopmile,deobfuscate the bytecode
				m_index = m_deobfuscator.getSource( classFile );
				m_gui.setSource( m_index.getSource(), lineNum );
				
				// set the highlighted tokens
				List<Token> obfuscatedTokens = Lists.newArrayList();
				List<Token> deobfuscatedTokens = Lists.newArrayList();
				for( Token token : m_index.tokens() )
				{
					Entry entry = m_index.getEntry( token );
					if( entryHasMapping( entry ) )
					{
						deobfuscatedTokens.add( token );
					}
					else if( entryIsObfuscatedIdenfitier( entry ) )
					{
						obfuscatedTokens.add( token );
					}
				}
				m_gui.setHighlightedTokens( obfuscatedTokens, deobfuscatedTokens );
			}
		}.start();
	}
}
