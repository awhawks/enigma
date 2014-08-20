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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.languages.TextLocation;
import com.strobel.decompiler.languages.java.ast.ConstructorDeclaration;
import com.strobel.decompiler.languages.java.ast.IdentifierExpression;
import com.strobel.decompiler.languages.java.ast.InvocationExpression;
import com.strobel.decompiler.languages.java.ast.Keys;
import com.strobel.decompiler.languages.java.ast.MemberReferenceExpression;
import com.strobel.decompiler.languages.java.ast.MethodDeclaration;
import com.strobel.decompiler.languages.java.ast.ParameterDeclaration;
import com.strobel.decompiler.languages.java.ast.SimpleType;

import cuchaz.enigma.mapping.ArgumentEntry;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.MethodEntry;

public class SourceIndexBehaviorVisitor extends SourceIndexVisitor
{
	private BehaviorEntry m_behaviorEntry;
	private Multiset<Entry> m_indices;
	
	public SourceIndexBehaviorVisitor( BehaviorEntry behaviorEntry )
	{
		m_behaviorEntry = behaviorEntry;
		m_indices = HashMultiset.create();
	}
	
	@Override
	public Void visitMethodDeclaration( MethodDeclaration node, SourceIndex index )
	{
		return recurse( node, index );
	}
	
	@Override
	public Void visitConstructorDeclaration( ConstructorDeclaration node, SourceIndex index )
	{
		return recurse( node, index );
	}
	
	@Override
	public Void visitInvocationExpression( InvocationExpression node, SourceIndex index )
	{
		MemberReference ref = node.getUserData( Keys.MEMBER_REFERENCE );
		ClassEntry classEntry = new ClassEntry( ref.getDeclaringType().getInternalName() );
		MethodEntry methodEntry = new MethodEntry( classEntry, ref.getName(), ref.getSignature() );
		if( node.getTarget() instanceof MemberReferenceExpression )
		{
			m_indices.add( methodEntry );
			index.addReference(
				((MemberReferenceExpression)node.getTarget()).getMemberNameToken(),
				new EntryReference<Entry,Entry>( methodEntry, m_behaviorEntry, m_indices.count( methodEntry ) )
			);
		}
		
		return recurse( node, index );
	}
	
	@Override
	public Void visitMemberReferenceExpression( MemberReferenceExpression node, SourceIndex index )
	{
		MemberReference ref = node.getUserData( Keys.MEMBER_REFERENCE );
		if( ref != null )
		{
			ClassEntry classEntry = new ClassEntry( ref.getDeclaringType().getInternalName() );
			FieldEntry fieldEntry = new FieldEntry( classEntry, ref.getName() );
			m_indices.add( fieldEntry );
			index.addReference(
				node.getMemberNameToken(),
				new EntryReference<Entry,Entry>( fieldEntry, m_behaviorEntry, m_indices.count( fieldEntry ) )
			);
		}
		
		return recurse( node, index );
	}
	
	@Override
	public Void visitSimpleType( SimpleType node, SourceIndex index )
	{
		TypeReference ref = node.getUserData( Keys.TYPE_REFERENCE );
		if( node.getIdentifierToken().getStartLocation() != TextLocation.EMPTY )
		{
			ClassEntry classEntry = new ClassEntry( ref.getInternalName() );
			m_indices.add( classEntry );
			index.addReference(
				node.getIdentifierToken(),
				new EntryReference<Entry,Entry>( classEntry, m_behaviorEntry, m_indices.count( classEntry ) )
			);
		}
		
		return recurse( node, index );
	}
	
	@Override
	public Void visitParameterDeclaration( ParameterDeclaration node, SourceIndex index )
	{
		ParameterDefinition def = node.getUserData( Keys.PARAMETER_DEFINITION );
		ClassEntry classEntry = new ClassEntry( def.getDeclaringType().getInternalName() );
		MethodDefinition methodDef = (MethodDefinition)def.getMethod();
		MethodEntry methodEntry = new MethodEntry( classEntry, methodDef.getName(), methodDef.getSignature() );
		ArgumentEntry argumentEntry = new ArgumentEntry( methodEntry, def.getPosition(), def.getName() );
		index.addDeclaration( node.getNameToken(), argumentEntry );
		
		return recurse( node, index );
	}
	
	@Override
	public Void visitIdentifierExpression( IdentifierExpression node, SourceIndex index )
	{
		MemberReference ref = node.getUserData( Keys.MEMBER_REFERENCE );
		if( ref != null )
		{
			ClassEntry classEntry = new ClassEntry( ref.getDeclaringType().getInternalName() );
			FieldEntry fieldEntry = new FieldEntry( classEntry, ref.getName() );
			m_indices.add( fieldEntry );
			index.addReference(
				node.getIdentifierToken(),
				new EntryReference<Entry,Entry>( fieldEntry, m_behaviorEntry, m_indices.count( fieldEntry ) )
			);
		}
		
		return recurse( node, index );
	}
}
