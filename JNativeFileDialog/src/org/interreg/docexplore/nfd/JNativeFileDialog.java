/*
    Copyright 2014 Alexander Burnett
    
    This file is part of JNativeFileDialog.

    JNativeFileDialog is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    JNativeFileDialog is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with JNativeFileDialog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.interreg.docexplore.nfd;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileFilter;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.WString;


public class JNativeFileDialog
{
	static interface NFD extends Library
	{
		public WString showOpenDialog(byte [] title, boolean acceptFiles, boolean acceptFolders, boolean multipleSelection,
			byte [] filterName, byte [] filter, byte [] initialDir);
		public WString showSaveDialog(byte [] title, byte [] filterName, byte [] filter, byte [] initialDir, byte [] initialName);
	}
	
	public String title = null;
	
	/**
	 * Bypass attempting to use native dialogs. If this is set before any invocation of JNativeFileDialog, no attempt will be made to load the native libraries.
	 */
	public static boolean noNative = false;
	
	public boolean acceptFiles = true, 
		acceptFolders = false, 
		multipleSelection = false;
	
	String filterName = null;
	Collection<String> filter = null;
	
	File initialFolder = null;
	String initialName = null;
	
	JFileChooser chooser = null;
	
	File [] selected = null;
	
	public JNativeFileDialog()
	{
		init();
	}
	
	public void removeFileFilter()
	{
		this.filterName = null;
		this.filter = null;
	}
	/**
	 * Filter the dialog view to the specified file formats.
	 * @param name The readable name of the formats (such as "Images")
	 * @param extensions A collection of extensions (such as "jpg")
	 */
	public void setFileFilter(String name, Collection<String> extensions)
	{
		this.filterName = name;
		this.filter = extensions;
	}
	/**
	 * Filter the dialog view to the specified file formats.
	 * @param name The readable name of the formats (such as "Images")
	 * @param extensions A collection of extensions (such as "jpg")
	 */
	public void setFileFilter(String name, String ... extensions)
	{
		this.filterName = name;
		this.filter = Arrays.asList(extensions);
	}
	
	/**
	 * Instructs the dialog to show directly a specific file when being displayed.
	 * @param file
	 */
	public void setCurrentFile(File file)
	{
		if (file == null)
		{
			initialFolder = null;
			initialName = null;
		}
		else if (file.isDirectory())
		{
			initialFolder = file;
			initialName = null;
		}
		else
		{
			initialFolder = file.getParentFile();
			initialName = file.getName();
		}
	}
	
	/**
	 * The selected files.
	 * @return The selected files, null if none
	 */
	public File [] getSelectedFiles() {return selected;}
	/**
	 * A selected file. The first of the array if there are more than one.
	 * @return
	 */
	public File getSelectedFile() {return selected == null || selected.length < 1 ? null : selected[0];}
	
	byte [] bfn, bf, bif, bin, bt;
	private void setupNative()
	{
		selected = null;
		bt = toUTF16LE(title);
		bfn = null;
		bf = null;
		if (filter != null)
		{
			bfn = toUTF16LE(filterName);
			
			String typeList = "";
			boolean first = true;
			for (String type : filter)
			{
				if (!first)
					typeList += ";";
				first = false;
				typeList += type;
			}
			bf = toUTF16LE(typeList);
		}
		
		bif = null;
		if (initialFolder != null) try {bif = toUTF16LE(initialFolder.getCanonicalPath());}
		catch (Exception e) {e.printStackTrace();}
		
		bin = toUTF16LE(initialName);
	}
	static byte [] toUTF16LE(String s)
	{
		if (s == null)
			return null;
		byte [] bytes = s.getBytes(Charset.forName("UTF-16LE"));
		bytes = Arrays.copyOf(bytes, bytes.length+2);
		bytes[bytes.length-2] = '\0';
		bytes[bytes.length-1] = '\0';
		return bytes;
	}
		
	private void setupJava()
	{
		selected = null;
		if (chooser == null)
			chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setFileSelectionMode(
			acceptFiles && acceptFolders ? JFileChooser.FILES_AND_DIRECTORIES :
			acceptFiles ? JFileChooser.FILES_ONLY : JFileChooser.DIRECTORIES_ONLY);
		chooser.setMultiSelectionEnabled(multipleSelection);
		if (filter != null)
			chooser.setFileFilter(new FileFilter()
			{
				public String getDescription() {return filterName;}
				public boolean accept(File f)
				{
					if (f.isDirectory())
						return true;
					int index = f.getName().lastIndexOf('.');
					return index >= 0 && filter.contains(f.getName().substring(index+1));
				}
			});
		else chooser.setFileFilter(null);
		if (initialFolder != null)
		{
			chooser.setCurrentDirectory(initialFolder);
			if (initialName != null)
				chooser.setSelectedFile(new File(initialFolder, initialName));
		}
	}
	
	private void readNativeResult(String res)
	{
		if (res.indexOf(';') < 0)
			selected = new File [] {new File(res)};
		else
		{
			String [] toks = res.split(";");
			selected = new File [toks.length-1];
			for (int i=1;i<toks.length;i++)
				selected[i-1] = new File(toks[0]+toks[i]);
		}
		setCurrentFile(selected[0].isDirectory() ? selected[0] : selected[0].getParentFile());
	}
	private void readJFileChooserResult()
	{
		if (multipleSelection)
			selected = chooser.getSelectedFiles();
		else selected = new File [] {chooser.getSelectedFile()};
		setCurrentFile(selected[0].isDirectory() ? selected[0] : selected[0].getParentFile());
	}
	
	public synchronized boolean showOpenDialog()
	{
		if (!noNative && nfd != null && (!acceptFolders || acceptFiles || canSelectFoldersNatively))
		{
			setupNative();
			WString res = null;
			synchronized (nfd) {res = nfd.showOpenDialog(bt, acceptFiles, acceptFolders, multipleSelection, bfn, bf, bif);}
			if (res == null)
				return false;
			readNativeResult(res.toString());
		}
		else
		{
			setupJava();
			if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				return false;
			readJFileChooserResult();
		}
		return true;
	}
	public synchronized boolean showSaveDialog()
	{
		if (!noNative && nfd != null)
		{
			setupNative();
			WString res = null;
			synchronized (nfd) {res = nfd.showSaveDialog(bt, bfn, bf, bif, bin);}
			if (res == null)
				return false;
			readNativeResult(res.toString());
		}
		else
		{
			setupJava();
			if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
				return false;
			readJFileChooserResult();
		}
		return true;
	}
	
	private static NFD nfd = null;
	private static boolean inited = false;
	private static boolean canSelectFoldersNatively = false;
	/**
	 * Extracts and loads the necessary native library. This is called automatically when first needed but can be called manually to reduce the loading time 
	 * when the first dialog is shown (a good idea at program startup).
	 */
	public static void init() {init(true);}
	public static void init(boolean retry)
	{
		if (inited)
			return;
		
		if (!noNative) try
		{
			String extension = null;
			
			File dir = new File(System.getProperty("user.home")+"/.jnativefiledialog");
			dir.mkdirs();
			String os = System.getProperty("os.name");
			
			if (os.toLowerCase().contains("win"))
			{
				extension = "dll";
				canSelectFoldersNatively = false;
			}
			else if (os.toLowerCase().contains("mac"))
			{
				extension = "dylib";
				canSelectFoldersNatively = true;
			}
			
			if (extension != null)
			{
				File libFile = new File(dir, "libNativeFileDialog."+extension);
				boolean exists = true;
				if (!libFile.exists())
				{
					exists = false;
					byte [] lib = null;
					if (os.toLowerCase().contains("win"))
					{
						String arch = System.getProperty("os.arch");
						arch = arch == null ? "" : arch;
						if (os.toLowerCase().contains("64") || arch.toLowerCase().contains("64"))
							lib = readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("org/interreg/docexplore/nfd/NativeFileDialog_x64.dll"));
						else lib = readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("org/interreg/docexplore/nfd/NativeFileDialog_x86.dll"));
					}
					else if (os.toLowerCase().contains("mac"))
						lib = readStream(Thread.currentThread().getContextClassLoader().getResourceAsStream("org/interreg/docexplore/nfd/libNativeFileDialog.dylib"));
					if (lib != null)
						writeFile(libFile, lib);
				}
				NativeLibrary.addSearchPath("NativeFileDialog", dir.getAbsolutePath());
				try {nfd = (NFD)Native.loadLibrary("NativeFileDialog", NFD.class);}
				catch (Throwable e)
				{
					if (exists && retry && libFile.delete())
						init(false);
					else e.printStackTrace();
				}
			}
		}
		catch (Throwable e) {e.printStackTrace();}
		
		inited = true;
	}
	static byte [] readStream(InputStream in) throws IOException
	{
		LinkedList<ByteBuffer> buffers = new LinkedList<ByteBuffer>();
		int size = 0;
		while (true)
		{
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			int read = in.read(buffer.array());
			if (read > 0)
			{
				buffers.add(buffer);
				buffer.limit(read);
				size += read;
			}
			if (read < 0)
				break;
		}
		ByteBuffer res = ByteBuffer.allocate(size);
		for (ByteBuffer buffer : buffers)
			res.put(buffer);
		in.close();
		return res.array();
	}
	static void writeFile(File file, byte [] bytes) throws IOException
	{
		RandomAccessFile raf = new RandomAccessFile(file, "rw");
		FileChannel channel = raf.getChannel();
		channel.truncate(0);
		channel.write(ByteBuffer.wrap(bytes));
		channel.close();
		raf.close();
	}
	
	@SuppressWarnings("serial")
	public static void main(String [] args)
	{
		JFrame win = new JFrame("Hey!");
		win.add(new JButton(new AbstractAction("Open...") {public void actionPerformed(ActionEvent e)
		{
			JNativeFileDialog dialog = new JNativeFileDialog();
			dialog.acceptFiles = true;
			dialog.acceptFolders = false;
			dialog.multipleSelection = true;
			dialog.setCurrentFile(new File("C:\\sci"));
			dialog.setFileFilter("Images", Arrays.asList("jpg", "jpeg", "png", "tiff", "gif"));
			System.out.println(dialog.showSaveDialog());
		}}){{setPreferredSize(new Dimension(300, 200));}});
		win.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		win.pack();
		win.setVisible(true);
	}
}
