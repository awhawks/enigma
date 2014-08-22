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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

public abstract class BoxHighlightPainter implements Highlighter.HighlightPainter
{
	private Color m_fillColor;
	private Color m_borderColor;
	
	protected BoxHighlightPainter( Color fillColor, Color borderColor )
	{
		m_fillColor = fillColor;
		m_borderColor = borderColor;
	}
	
	@Override
	public void paint( Graphics g, int start, int end, Shape shape, JTextComponent text )
	{
		Rectangle bounds = getBounds( text, start, end );
		
		// fill the area
		g.setColor( m_fillColor );
		g.fillRoundRect( bounds.x, bounds.y, bounds.width, bounds.height, 4, 4 );
		
		// draw a box around the area
		g.setColor( m_borderColor );
		g.drawRoundRect( bounds.x, bounds.y, bounds.width, bounds.height, 4, 4 );
	}
	
	protected static Rectangle getBounds( JTextComponent text, int start, int end )
	{
		try
		{
			// determine the bounds of the text
			Rectangle bounds = text.modelToView( start ).union( text.modelToView( end ) );
			
			// adjust the box so it looks nice
			bounds.x -= 2;
			bounds.width += 2;
			bounds.y += 1;
			bounds.height -= 2;
			
			return bounds;
		}
		catch( BadLocationException ex )
		{
			throw new Error( ex );
		}
	}
}
