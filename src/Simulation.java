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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.JComponent;
import javax.swing.RepaintManager;

import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCPortOut;

public class Simulation extends JComponent implements Runnable {
	private static final long serialVersionUID = 1L;
	private Manager manager;
	private OSCPortOut oscPort;

	private int currentFrame = 0;	
	private long lastFrameTime = -1;
	private int session_id = -1;

	Shape vision;
	private Stroke gestureStroke = new BasicStroke(1.0f);

	Finger selectedCursor = null;
	int lastX  = -1;
	int lastY  = -1;
	int clickX = 0;
	int clickY = 0;

	Vector<Integer> stickyCursors = new Vector<Integer>();
	Vector<Integer> jointCursors = new Vector<Integer>();

	private boolean running = false;
	
	int window_width;
	int window_height;

	
	MouseEvent lastPressedEvent;
	
	public Simulation(Manager manager, String host, int port) {
		super();
		this.manager=manager;

		try { oscPort = new OSCPortOut(java.net.InetAddress.getByName(host),port); }
		catch (Exception e) { oscPort = null; }

		String  os = System.getProperty("os.name");
		if (os.equals("Mac OS X")) RepaintManager.currentManager(this).setDoubleBufferingEnabled(false);

		// listens to the mouseDown event
		addMouseListener (
			new MouseAdapter () {
				public void mousePressed (MouseEvent evt) {
					mouse_pressed(evt);
				}
			}
		);

		// listens to the mouseDragged event
		addMouseMotionListener (
			new MouseMotionAdapter () {
				public void mouseDragged (MouseEvent evt) {
					mouse_dragged(evt);
				}
			}
		);
		
		// listens to the mouseReleased event
		addMouseListener (
			new MouseAdapter () {
				public void mouseReleased (MouseEvent evt) {
					mouse_released(evt);
				}
			}
		);
		
		addComponentListener(
			new ComponentAdapter() {
	            public void componentResized(ComponentEvent e) {
	            	window_width = e.getComponent().getWidth();
	            	window_height = e.getComponent().getHeight();
	            	vision = new Area(new Rectangle2D.Float(0,0,window_width,window_height));
	            	reset();
	            }
			}
		);
	}

	private void sendOSC(OSCPacket packet) {
		try { oscPort.send(packet); }
		catch (java.io.IOException e) {}
	}

	private void cursorDelete() {
		
		OSCBundle cursorBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		Enumeration<Integer> cursorList = manager.cursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);
		}

		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);
		
		cursorBundle.addPacket(aliveMessage);
		cursorBundle.addPacket(frameMessage);

		sendOSC(cursorBundle);
	}

	private void cursorMessage() {

		OSCBundle cursorBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");

		Enumeration<Integer> cursorList = manager.cursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);
		}

		Finger cursor = selectedCursor;
		Point point = cursor.getPosition();
		float xpos = (point.x)/(float)window_width;
		if (manager.invertx) xpos = 1 - xpos;
		float ypos = (point.y)/(float)window_height;
		if (manager.inverty) ypos = 1 - ypos;
		OSCMessage setMessage = new OSCMessage("/tuio/2Dcur");
		setMessage.addArgument("set");
		setMessage.addArgument(cursor.session_id);
		setMessage.addArgument(xpos);
		setMessage.addArgument(ypos);
		setMessage.addArgument(cursor.xspeed);
		setMessage.addArgument(cursor.yspeed);
		setMessage.addArgument(cursor.maccel);

		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);
		
		cursorBundle.addPacket(aliveMessage);
		cursorBundle.addPacket(setMessage);
		cursorBundle.addPacket(frameMessage);

		if (manager.verbose) {
			System.out.println("set cur "+cursor.session_id+" "+xpos+" "+ypos+" "+cursor.xspeed+" "+cursor.yspeed+" "+cursor.maccel);
		}
		sendOSC(cursorBundle);
	}

	public void aliveMessage() {

		OSCBundle oscBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dobj");
		aliveMessage.addArgument("alive");
		
		currentFrame++;
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dobj");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(currentFrame);

		oscBundle.addPacket(aliveMessage);
		oscBundle.addPacket(frameMessage);
		
		sendOSC(oscBundle);
	}

	private void completeCursorMessage() {
		Vector<OSCMessage> messageList = new Vector<OSCMessage>();
		
		OSCMessage frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);
		
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		
		Enumeration<Integer> cursorList = manager.cursorList.keys();
		while (cursorList.hasMoreElements()) {
			Integer s_id = cursorList.nextElement();
			aliveMessage.addArgument(s_id);

			Finger cursor = manager.cursorList.get(s_id);
			Point point = cursor.getPosition();
					
			float xpos = point.x/(float)window_width;
			if (manager.invertx) xpos = 1 - xpos;
			float ypos = point.y/(float)window_height;
			if (manager.inverty) ypos = 1 - ypos;

			OSCMessage setMessage = new OSCMessage("/tuio/2Dcur");
			setMessage.addArgument("set");
			setMessage.addArgument(s_id);
			setMessage.addArgument(xpos);
			setMessage.addArgument(ypos);
			setMessage.addArgument(cursor.xspeed);
			setMessage.addArgument(cursor.yspeed);
			setMessage.addArgument(cursor.maccel);
			messageList.addElement(setMessage);
		}
		
		int i;
		for (i=0;i<(messageList.size()/10);i++) {
			OSCBundle oscBundle = new OSCBundle();			
			oscBundle.addPacket(aliveMessage);
			
			for (int j=0;j<10;j++)
				oscBundle.addPacket((OSCPacket)messageList.elementAt(i*10+j));
			
			oscBundle.addPacket(frameMessage);
			sendOSC(oscBundle);
		} 
		
		if ((messageList.size()%10!=0) || (messageList.size()==0)) {
			OSCBundle oscBundle = new OSCBundle();			
			oscBundle.addPacket(aliveMessage);
			
			for (int j=0;j<messageList.size()%10;j++)
				oscBundle.addPacket((OSCPacket)messageList.elementAt(i*10+j));	
			
			oscBundle.addPacket(frameMessage);
			sendOSC(oscBundle);
		}
	}

	public void quit() {
		reset();
		running = false;
	}

	public void reset() {
		session_id = -1;
		stickyCursors.clear();
		jointCursors.clear();
		
		lastFrameTime = -1;
		
		OSCBundle objBundle = new OSCBundle();
		OSCMessage aliveMessage = new OSCMessage("/tuio/2Dobj");
		aliveMessage.addArgument("alive");

		OSCMessage frameMessage = new OSCMessage("/tuio/2Dobj");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);

		objBundle.addPacket(aliveMessage);
		objBundle.addPacket(frameMessage);		
		sendOSC(objBundle);
		
		OSCBundle curBundle = new OSCBundle();
		aliveMessage = new OSCMessage("/tuio/2Dcur");
		aliveMessage.addArgument("alive");
		
		frameMessage = new OSCMessage("/tuio/2Dcur");
		frameMessage.addArgument("fseq");
		frameMessage.addArgument(-1);
		
		curBundle.addPacket(aliveMessage);
		curBundle.addPacket(frameMessage);		
		sendOSC(curBundle);
	}

	public void paint(Graphics g) {
		update(g);
	}

	public void update(Graphics g) {
	
		// setup the graphics environment
		Graphics2D g2 = (Graphics2D)g;
		if (manager.antialiasing) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		} else {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);			
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		}

		// paint the cursors
		Enumeration<Finger> cursorList = manager.cursorList.elements();
		while (cursorList.hasMoreElements()) {
			Finger cursor = cursorList.nextElement();
			Vector<Point> gesture = cursor.getPath();
			if (gesture.size()>0) {
				g2.setPaint(Color.blue);
				g2.setStroke(gestureStroke);
				Point start = gesture.elementAt(0);
				for (int i=0;i<gesture.size();i++) {
					Point end  = gesture.elementAt(i);
					g2.draw(new Line2D.Double(start.getX(),start.getY(),end.getX(),end.getY()));
					start = end;
				}
				if (jointCursors.contains(cursor.session_id)) g2.setPaint(Color.darkGray);
				else g2.setPaint(Color.lightGray);
				g2.fill(new Ellipse2D.Double(start.getX()-5,start.getY()-5,10,10));
			}
		}
	}

	
	public void mouse_dragged (MouseEvent evt) {

		long currentFrameTime = System.currentTimeMillis();
		long dt = currentFrameTime - lastFrameTime;
		if (dt<16) return;

		Point pt = evt.getPoint();
		int x = (int)pt.getX();
		int y = (int)pt.getY();
		
		if (selectedCursor!=null){
				if (vision.contains(pt)) {
					if(selectedCursor!=null) {
					if (jointCursors.contains(selectedCursor.session_id)) {
						Point selPoint = selectedCursor.getPosition();
						int dx = pt.x - selPoint.x;
						int dy = pt.y - selPoint.y;
					
						Enumeration<Integer> joints = jointCursors.elements();
						while (joints.hasMoreElements()) {
							int jointId = joints.nextElement();
							if (jointId == selectedCursor.session_id) continue;
							Finger joint_cursor = manager.getCursor(jointId);
							Point joint_point = joint_cursor.getPosition();
							manager.updateCursor(joint_cursor, joint_point.x+dx,joint_point.y+dy);
						}
						manager.updateCursor(selectedCursor,pt.x,pt.y);
						completeCursorMessage();
					} else {
						manager.updateCursor(selectedCursor,pt.x,pt.y);
						cursorMessage();
					} }
				} else {
					selectedCursor.stop();
					cursorMessage();
					if (manager.verbose) System.out.println("del cur "+selectedCursor.session_id);
					if (stickyCursors.contains(selectedCursor.session_id)) stickyCursors.removeElement(selectedCursor.session_id);
					if (jointCursors.contains(selectedCursor.session_id)) jointCursors.removeElement(selectedCursor.session_id);
					manager.terminateCursor(selectedCursor);
					cursorDelete();
					selectedCursor = null;
				}
			} else {
				if (vision.contains(pt)) {
					session_id++;
					if (manager.verbose) System.out.println("add cur "+session_id);
					selectedCursor = manager.addCursor(session_id,x,y);
					cursorMessage();
					if ((evt.getModifiers() & InputEvent.SHIFT_MASK) > 0) stickyCursors.addElement(selectedCursor.session_id);
				}
			}

			lastX = x;
			lastY = y;			
			lastFrameTime = currentFrameTime;
	}

	public void mouse_pressed (MouseEvent evt) {
			
		int x = evt.getX();
		int y = evt.getY();

		Enumeration<Finger> cursorList = manager.cursorList.elements();
		while (cursorList.hasMoreElements()) {
			Finger cursor = cursorList.nextElement();
			Point point = cursor.getPosition();
			if (point.distance(x,y)<7) {

				int selCur = -1;
				if (selectedCursor!=null) selCur = selectedCursor.session_id;
				if (((evt.getModifiers() & InputEvent.SHIFT_MASK) > 0) && selCur != cursor.session_id) {
					if (manager.verbose) System.out.println("del cur "+cursor.session_id);
					stickyCursors.removeElement(cursor.session_id);
					if (jointCursors.contains(cursor.session_id)) jointCursors.removeElement(cursor.session_id);
					manager.terminateCursor(cursor);
					cursorDelete();
					selectedCursor = null;
					return;
				} else if ((evt.getModifiers() & InputEvent.CTRL_MASK) > 0) {
					if (jointCursors.contains(cursor.session_id)) jointCursors.removeElement(cursor.session_id);
					else jointCursors.addElement(cursor.session_id);
					repaint();
					return;
				} else {
					selectedCursor = cursor;
					lastX = x;
					lastY = y;
					return;
				} 
			}
		}
			
		if ((evt.getModifiers() & InputEvent.CTRL_MASK) > 0) return;
		
		if (vision.contains(new Point(x, y))) {
			
			session_id++;
			if (manager.verbose) System.out.println("add cur "+session_id);
			selectedCursor = manager.addCursor(session_id,x,y);
			cursorMessage();
			if ((evt.getModifiers() & InputEvent.SHIFT_MASK) > 0) stickyCursors.addElement(selectedCursor.session_id);
			return;
		}

		selectedCursor = null;
		lastX = -1;
		lastY = -1;
	}

	public void mouse_released (MouseEvent evt) {

		if ( (selectedCursor!=null) )  {
			
			if (!stickyCursors.contains(selectedCursor.session_id)) {
				selectedCursor.stop();
				cursorMessage();
				if (manager.verbose) System.out.println("del cur "+ selectedCursor.session_id);
				if (jointCursors.contains(selectedCursor.session_id)) jointCursors.removeElement(selectedCursor.session_id);
				manager.terminateCursor(selectedCursor);
				cursorDelete();
			} else {
				selectedCursor.stop();
				cursorMessage();
			}
			
			selectedCursor = null;
		}
	}

	public void enablePeriodicMessages() {
		if (!running) {
			running = true;
			new Thread(this).start();
		}

	}

	public void disablePeriodicMessages() {
		running = false;
	}

	// send table state every second
	public void run() {
		running = true;
		while(running) {
			try { Thread.sleep(1000); }
			catch (Exception e) {}

			long currentFrameTime = System.currentTimeMillis();
                	long dt = currentFrameTime - lastFrameTime;
                	if (dt>1000) {
				completeCursorMessage();
			}
		}
	}
}
