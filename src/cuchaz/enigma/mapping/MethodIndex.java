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
package cuchaz.enigma.mapping;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class MethodIndex implements Serializable
{
	private static final long serialVersionUID = -4409570216084263978L;
	
	private String m_obfName;
	private String m_deobfName;
	private String m_obfSignature;
	private String m_deobfSignature;
	private Map<Integer,ArgumentIndex> m_arguments;
	
	public MethodIndex( String obfName, String obfSignature, String deobfName, String deobfSignature )
	{
		m_obfName = obfName;
		m_deobfName = deobfName;
		m_obfSignature = obfSignature;
		m_deobfSignature = deobfSignature;
		m_arguments = new TreeMap<Integer,ArgumentIndex>();
	}

	public String getObfName( )
	{
		return m_obfName;
	}
	
	public String getDeobfName( )
	{
		return m_deobfName;
	}
	public void setDeobfName( String val )
	{
		m_deobfName = val;
	}
	
	public String getObfSignature( )
	{
		return m_obfSignature;
	}
	
	public String getDeobfSignature( )
	{
		return m_deobfSignature;
	}
	public void setDeobfSignature( String val )
	{
		m_deobfSignature = val;
	}
	
	public String getObfArgumentName( int index )
	{
		ArgumentIndex argumentIndex = m_arguments.get( index );
		if( argumentIndex != null )
		{
			return argumentIndex.getObfName();
		}
		
		return null;
	}
	
	public String getDeobfArgumentName( int index )
	{
		ArgumentIndex argumentIndex = m_arguments.get( index );
		if( argumentIndex != null )
		{
			return argumentIndex.getDeobfName();
		}
		
		return null;
	}
	
	public void setArgumentName( int index, String obfName, String deobfName )
	{
		ArgumentIndex argumentIndex = m_arguments.get( index );
		if( argumentIndex == null )
		{
			argumentIndex = new ArgumentIndex( obfName, deobfName );
			m_arguments.put( index, argumentIndex );
		}
		else
		{
			argumentIndex.setDeobfName( deobfName );
		}
	}
	
	@Override
	public String toString( )
	{
		StringBuilder buf = new StringBuilder();
		buf.append( "\t" );
		buf.append( m_obfName );
		buf.append( " <-> " );
		buf.append( m_deobfName );
		buf.append( "\n" );
		buf.append( "\t" );
		buf.append( m_obfSignature );
		buf.append( " <-> " );
		buf.append( m_deobfSignature );
		buf.append( "\n" );
		buf.append( "\tArguments:\n" );
		for( ArgumentIndex argumentIndex : m_arguments.values() )
		{
			buf.append( "\t\t" );
			buf.append( argumentIndex.getObfName() );
			buf.append( " <-> " );
			buf.append( argumentIndex.getDeobfName() );
			buf.append( "\n" );
		}
		return buf.toString();
	}
}