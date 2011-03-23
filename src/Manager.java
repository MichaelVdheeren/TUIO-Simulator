/*
    TUIO Simulator - part of the reacTIVision project
    http://reactivision.sourceforge.net/

    Copyright (c) 2005-2009 Martin Kaltenbrunner <mkalten@iua.upf.edu>

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.util.Hashtable;

import javax.swing.JFrame;

public class Manager {
	public boolean verbose = false;
	public boolean antialiasing = false;
	public boolean collision = false;
	
	public boolean invertx = false;
	public boolean inverty = false;
	public boolean inverta = false;
	
	public Hashtable<Integer,Finger> cursorList = new Hashtable<Integer,Finger>();	
	private JFrame parent;
	
	public Manager(JFrame parent, String config) {
		this.parent = parent;
		reset();
	}
	
	public final void reset() {
		cursorList.clear();
		parent.repaint();	
	}
	
	public final Finger addCursor(int s_id, int x, int y) {
		
		Finger cursor = new Finger(s_id,x,y);
		cursorList.put(s_id, cursor);
		parent.repaint();
		return cursor;
	}

	public final void updateCursor(Finger cursor, int x, int y) {

		cursor.update(x,y);
		parent.repaint();
	}

	public final Finger getCursor(int s_id) {
		return cursorList.get(s_id);
	}
	
	public final void terminateCursor(Finger cursor) {
		cursorList.remove(cursor.session_id);
		parent.repaint();
	}
 

}
