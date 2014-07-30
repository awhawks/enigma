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
package cuchaz.enigma;

import java.io.File;

import cuchaz.enigma.gui.Gui;

public class Main
{
	public static void main( String[] args )
	throws Exception
	{
		Gui gui = new Gui();
		
		// parse command-line args
		if( args.length >= 1 )
		{
			gui.getController().openJar( getFile( args[0] ) );
		}
		if( args.length >= 2 )
		{
			gui.getController().openMappings( getFile( args[1] ) );
		}
	}
	
	private static File getFile( String path )
	{
		// expand ~ to the home dir
		if( path.startsWith( "~" ) )
		{
			// get the home dir
			File dirHome = new File( System.getProperty( "user.home" ) );
			
			// is the path just ~/ or is it ~user/ ?
			if( path.startsWith( "~/" ) )
			{
				return new File( dirHome, path.substring( 2 ) );
			}
			else
			{
				return new File( dirHome.getParentFile(), path.substring( 1 ) );
			}
		}
		
		return new File( path );
	}
}
