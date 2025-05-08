package com.ganteater.ae.desktop.editor;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.ganteater.ae.AEManager;
import com.ganteater.ae.CommandException;
import com.ganteater.ae.desktop.ui.AEFrame;
import com.ganteater.ae.desktop.ui.DialogPopupMenu;
import com.ganteater.ae.processor.LocalDataSource;
import com.ganteater.ae.processor.SQLQuery;
import com.ganteater.ae.processor.Processor;
import com.ganteater.ae.util.xml.easyparser.Node;

public class DatabaseQueryPanel extends JPanel
		implements TableModel, Runnable, ActionListener, KeyListener, AeEditPanel {

	private static final String CONNECTION_ATTR_NAME = "connection";
	private static final long serialVersionUID = 1L;
	private static final String ADD_NEW_HELPER = "Add this helper";
	private static final int MAX_NUMBER_ROWS = 65000;

	static Properties fCallableHelperProperties = new Properties();

	private Editor editor = new Editor();

	private String panelName;
	private List<String[]> rowList = new ArrayList<>();
	private String[] columnNames = new String[0];
	private transient SQLQuery query = null;

	private transient List<TableModelListener> tableModelListener = new ArrayList<>();
	private transient TaskEditor taskEditor;

	private JSplitPane splitPane = new JSplitPane();
	private DialogPopupMenu popupMenu = new DialogPopupMenu(editor);
	private JTable resultTable = new JTable();
	private JButton runButton = new JButton("Run", AEFrame.getIcon("run.png"));
	private JButton commitButton = new JButton("Commit");
	private JButton rollbackButton = new JButton("Rollback");
	private JComboBox<String> connectionsComboBox = new JComboBox<>();

	private List<Integer> widthList = new ArrayList<>();

	private transient Thread runThread;

	public DatabaseQueryPanel(TaskEditor taskEditor, Node node) throws CommandException {
		setLayout(new BorderLayout());
		this.taskEditor = taskEditor;
		this.panelName = StringUtils.defaultIfEmpty(node.getAttribute("name"), getClass().getSimpleName());

		Node taskNode = getManager().getConfigNode();
		
		
		Processor taskProcessor = taskEditor.getTaskProcessor();
		
		LocalDataSource.createDBConnection(taskNode, taskProcessor);
		
		taskProcessor.taskNode(node);
		LocalDataSource.createDBConnection(node, taskProcessor);

		splitPane.setTopComponent(new JScrollPane(editor));

		editor.addKeyListener(new CodeHelper(this, taskEditor.getLogger()));
		editor.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
					showPopupMenu();
				}
			}

		});

		popupMenu.add(createMenuItem("select * from"));

		popupMenu.addSeparator();
		popupMenu.add(createMenuItem(ADD_NEW_HELPER));
		popupMenu.add(createMenuItem("Run statement"));

		runButton.addActionListener(this);
		runButton.setToolTipText("Run F9");
		final PopupMenu popup = new PopupMenu();
		popup.add("Toggle repeat mode");
		popup.addActionListener(e -> {
			if (runThread == null) {
				runThread = new Thread() {

					@Override
					public void run() {
						while (runThread != null) {
							if (splitPane.isVisible()) {
								runButton.doClick();
								editor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
							}
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
						}
						editor.setCursor(Cursor.getDefaultCursor());
					}
				};
				runThread.start();
			} else {
				runThread = null;
			}
		});

		runButton.add(popup);
		runButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					popup.show(runButton, 0, 0);
				}
			}
		});

		JToolBar toolBar = new JToolBar();
		toolBar.add(connectionsComboBox);

		toolBar.add(new JSeparator(SwingConstants.VERTICAL));

		toolBar.add(runButton);

		commitButton.addActionListener(e -> {
			try {
				query.commit();
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(splitPane, e1.getMessage());
				e1.printStackTrace();
			}
		});

		toolBar.add(commitButton);

		rollbackButton.addActionListener(e -> {
			try {
				query.rollback();
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(splitPane, e1.getMessage());
				e1.printStackTrace();
			}
		});
		toolBar.add(rollbackButton);

		JButton theBreakButton = new JButton("Break");
		theBreakButton.addActionListener(e -> {
			try {
				query.close();
				query = new SQLQuery(getConnectionName());
			} catch (Exception e1) {
				JOptionPane.showMessageDialog(splitPane, e1.getMessage());
				e1.printStackTrace();
			}
		});
		toolBar.add(theBreakButton);

		JButton theSaveButton = new JButton(AEFrame.getIcon("save.png"));
		theSaveButton.setToolTipText("Save in recipe");
		theSaveButton.addActionListener(e -> saveSqlNote());
		toolBar.add(theSaveButton);

		JPanel theToolPanel = new JPanel(new BorderLayout());
		theToolPanel.add(toolBar, BorderLayout.WEST);

		add(theToolPanel, BorderLayout.NORTH);

		splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		splitPane.setOneTouchExpandable(true);

		add(splitPane, BorderLayout.CENTER);

		JScrollPane scroll = new JScrollPane(resultTable);
		splitPane.setBottomComponent(scroll);

		try (FileInputStream theFileInputStream = new FileInputStream("sql.helper.properties")) {
			fCallableHelperProperties.load(theFileInputStream);

			for (Enumeration<?> en = fCallableHelperProperties.propertyNames(); en.hasMoreElements();) {
				String theName = (String) en.nextElement();
				popupMenu.add(createMenuItem(theName), 0);
			}
		} catch (IOException e1) {
			System.err.println(e1.getMessage());
		}

		Set<String> connectionNames = LocalDataSource.getConnectionNames();
		for (String connectionName : connectionNames) {
			connectionsComboBox.addItem(connectionName);
		}
		if (!connectionNames.isEmpty()) {
			connectionsComboBox.setSelectedIndex(0);
		}
		connectionsComboBox.addActionListener(e -> {
			fillNoteText((String) connectionsComboBox.getSelectedItem());
			initQuery();
		});

		String selectedConnection = (String) connectionsComboBox.getSelectedItem();
		fillNoteText(selectedConnection);
	}

	private void fillNoteText(String selectedConnection) {
		Node taskNode = taskEditor.getTaskNode();
		Node[] nodes = taskNode.findNodes("Note", "name", panelName);
		for (Node node : nodes) {
			if (node != null && StringUtils.equals(node.getAttribute(CONNECTION_ATTR_NAME), selectedConnection)) {
				Node[] textNodes = node.getTextNodes();
				if (textNodes.length > 0) {
					editor.setText(textNodes[0].getText());
				}
			}
		}
	}

	private void initQuery() {
		String connectionName = getConnectionName();
		try {
			query = new SQLQuery(connectionName);

			boolean autoCommit = query.connection().getAutoCommit();
			rollbackButton.setVisible(!autoCommit);
			commitButton.setVisible(!autoCommit);

		} catch (Exception e) {
			String message = ExceptionUtils.getRootCauseMessage(e);
			IllegalArgumentException illegalArgumentException = new IllegalArgumentException(
					"Connection: " + connectionName + ", Error: " + message, e);
			taskEditor.createLog(panelName, false).error(ExceptionUtils.getStackTrace(illegalArgumentException));
			throw illegalArgumentException;
		}
	}

	public String getConnectionName() {
		int selectedIndex = connectionsComboBox.getSelectedIndex();
		return connectionsComboBox.getItemAt(selectedIndex < 0 ? 0 : selectedIndex);
	}

	private JMenuItem createMenuItem(String string) {
		JMenuItem theJMenuItem = new JMenuItem(string);
		theJMenuItem.addActionListener(this);
		return theJMenuItem;
	}

	private JMenuItem createCallableMenuItem(String string) {
		if (getRunLine() != null && string != null) {
			fCallableHelperProperties.setProperty(string, getRunLine()[0]);
		}
		JMenuItem theJMenuItem = new JMenuItem(string);
		theJMenuItem.addActionListener(this);
		return theJMenuItem;
	}

	public void setDividerLocation(int aDevideLocation) {
		splitPane.setDividerLocation(aDevideLocation);
	}

	public void run() {

		saveColumnsWidth();

		runButton.setEnabled(false);

		try {
			String[] commands = getRunLine();

			for (String command : commands) {
				if (!command.startsWith("--")) {
					runSqlCommand(command.trim());
				}
			}
		} catch (SQLException e1) {
			runThread = null;
			JOptionPane.showMessageDialog(this, e1.getMessage());
			e1.printStackTrace();
		} catch (Exception e1) {
			runThread = null;
			JOptionPane.showMessageDialog(this, StringUtils.defaultString(e1.getMessage(), e1.getClass().getName()));
			e1.printStackTrace();
		}

		int theListenersCount = tableModelListener.size();
		for (int i = 0; i < theListenersCount; i++) {
			TableModelListener theTableModelListener = tableModelListener.get(i);
			theTableModelListener.tableChanged(new TableModelEvent(this));
		}

		runButton.setEnabled(true);
		resultTable.setModel(this);
	}

	private void runSqlCommand(String request) throws SQLException {
		ResultSet theResultSet;
		if (query == null) {
			initQuery();
		}

		StringBuilder buffer = new StringBuilder(
				"Connection: " + connectionsComboBox.getSelectedItem() + "\nQuery: " + request + "\n");
		try {
			theResultSet = query.forResultSet(request);

			int theColumnCount = theResultSet.getMetaData().getColumnCount();

			rowList.clear();

			buffer.append("Column list:\n");
			columnNames = new String[theColumnCount];
			for (int i = 1; i < theColumnCount + 1; i++) {
				columnNames[i - 1] = theResultSet.getMetaData().getColumnName(i);
				String string = columnNames[i - 1];
				buffer.append(i + ". " + theResultSet.getMetaData().getColumnClassName(i) + " " + string.toLowerCase()
						+ ";\n");
			}

			if (runThread == null) {
				taskEditor.createLog(panelName, false).debug(buffer.toString());
			}

			buffer = new StringBuilder();

			while (theResultSet.next()) {

				String[] theRec = new String[theColumnCount];
				for (int theColumn = 1; theColumn < theColumnCount + 1; theColumn++) {
					theRec[theColumn - 1] = query.getStringValue(theResultSet, theColumn);
				}

				rowList.add(theRec);

				buffer.append(StringUtils.join(theRec, ", "));
				buffer.append("\n");

				if (rowList.size() > MAX_NUMBER_ROWS) {
					for (int theColumn = 1; theColumn < theColumnCount + 1; theColumn++) {
						theRec[theColumn - 1] = "...";
					}
					rowList.add(theRec);
					break;
				}

			}

			if (runThread == null) {
				taskEditor.createLog(panelName, false).info(buffer.toString());
			}

			TableColumnModel columnModel = resultTable.getColumnModel();

			if (theColumnCount != columnModel.getColumnCount()) {
				resultTable.setModel(new DefaultTableModel());
				resultTable.setModel(this);
			}

			resultTable.invalidate();
			resultTable.repaint();

			if (columnModel.getColumnCount() == widthList.size()) {
				for (int i = 0; i < widthList.size() && columnModel.getColumnCount() > i; i++) {
					int width = widthList.get(i);
					TableColumn column = columnModel.getColumn(i);
					column.setPreferredWidth(width);
					column.setMinWidth(3);
					column.setMaxWidth(1000);
				}
			}

		} catch (Exception e) {
			runThread = null;
			columnNames = new String[0];
			resultTable.setModel(new DefaultTableModel());
			resultTable.setModel(this);

			resultTable.invalidate();
			resultTable.repaint();

			String message = e.getMessage();
			if (!"No results were returned by the query.".equalsIgnoreCase(message)) {
				buffer.append("Error: " + message);
				taskEditor.createLog(panelName, false).error(buffer.toString());
				throw e;
			} else {
				taskEditor.createLog(panelName, false).debug(buffer.toString());
			}
		}

	}

	private void saveColumnsWidth() {
		widthList.clear();
		Enumeration<TableColumn> columns = resultTable.getColumnModel().getColumns();
		while (columns.hasMoreElements()) {
			TableColumn tableColumn = columns.nextElement();
			int width = tableColumn.getWidth();
			widthList.add(width);
		}
	}

	protected String[] getRunLine() {
		String selectedText = editor.getSelectedText();
		String[] lines = null;

		if (selectedText == null) {
			selectedText = editor.getText();

			int caretPosition = editor.getCaretPosition();
			int begin = selectedText.substring(0, caretPosition).lastIndexOf('\n');
			int end = StringUtils.indexOf(selectedText, '\n', caretPosition - 1);

			int beginIndex = begin < 0 ? 0 : begin;
			int endIndex = end < 0 ? selectedText.length() : end;
			String statement = selectedText.substring(beginIndex, endIndex);

			editor.select(beginIndex, endIndex);

			lines = new String[] { StringUtils.trim(statement) };

		} else {
			lines = selectedText.split("(?<=;)\\s+");
		}

		return lines;
	}

	@Override
	public int getRowCount() {
		return rowList.size();
	}

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public String getColumnName(int columnIndex) {
		return columnNames[columnIndex];
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return true;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		String[] theText = rowList.get(rowIndex);
		if (theText != null && columnIndex < theText.length)
			return theText[columnIndex];
		return null;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		// NOT IMPLEMENTED
	}

	@Override
	public void addTableModelListener(TableModelListener l) {
		tableModelListener.add(l);
	}

	@Override
	public void removeTableModelListener(TableModelListener l) {
		tableModelListener.remove(l);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if ("Run".equals(e.getActionCommand())) {
			runQuery();
		}
		int caretPosition = editor.getCaretPosition();
		if ("select * from".equals(e.getActionCommand())) {
			editor.insert("SELECT * FROM ", caretPosition);
		}
		if (ADD_NEW_HELPER.equals(e.getActionCommand())) {
			String inputValue = JOptionPane.showInputDialog("Please input helper name");
			popupMenu.add(createCallableMenuItem(inputValue), 0);
			try (FileOutputStream theFileOutputStream = new FileOutputStream("sql.helper.properties")) {
				fCallableHelperProperties.store(theFileOutputStream, null);
			} catch (IOException e1) {
				taskEditor.getLogger().error(e1.getMessage());
			}
		} else {
			String theHelperLine = fCallableHelperProperties.getProperty(e.getActionCommand());
			if (theHelperLine != null) {

				String selectedValue = "";
				try {
					String selectedText = editor.getSelectedText();
					if ((selectedText == null || selectedText.isEmpty()) && caretPosition > 1) {
						String text = editor.getText();
						if (text.charAt(caretPosition - 1) != ' ') {
							int startPosition = text.lastIndexOf(' ', caretPosition);
							if (startPosition >= 0) {
								editor.setSelectionStart(startPosition + 1);
							} else {
								editor.setSelectionStart(0);
							}
							editor.setSelectionEnd(caretPosition);
							selectedText = editor.getSelectedText();
						}
					}

					if (selectedText == null) {
						selectedText = "";
					}

					String theCommandLine = theHelperLine.replace("%SELECT%", selectedText);

					theCommandLine = taskEditor.getTaskProcessor().replaceProperties(theCommandLine);
					theCommandLine = StringEscapeUtils.unescapeXml(theCommandLine);

					if (theCommandLine.trim().charAt(0) != '$') {
						List<String> forArrayValues = query.forArrayValues(theCommandLine);
						if (forArrayValues.size() == 1) {
							selectedValue = forArrayValues.get(0);
						} else if (forArrayValues.size() > 1) {
							selectedValue = (String) JOptionPane.showInputDialog(null, "Choose one", "Input",
									JOptionPane.INFORMATION_MESSAGE, null, forArrayValues.toArray(),
									forArrayValues.get(0));
						}
					} else {
						selectedValue = theCommandLine;
					}

					if (selectedText != null && !selectedText.isEmpty()) {
						editor.replaceRange(selectedValue, editor.getSelectionStart(), editor.getSelectionEnd());
					} else {
						editor.insert(selectedValue, caretPosition);
					}

				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}

	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_F9)
			actionPerformed(new ActionEvent(this, 0, "Run"));
		if (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown()) {
			showPopupMenu();
		}
	}

	private void showPopupMenu() {
		Point magicCaretPosition = editor.getLocation();

		Point caretPosition = editor.getCaret().getMagicCaretPosition();
		int theX = 0;
		int theY = 0;

		if (caretPosition != null) {
			theX = (int) (magicCaretPosition.getX() + caretPosition.getX());
			theY = (int) (magicCaretPosition.getY() + editor.getCaret().getMagicCaretPosition().getY());
		}

		popupMenu.show(editor, theX, theY);
	}

	public void keyReleased(KeyEvent e) {
		// NOT IMPLEMENTED
	}

	public void keyTyped(KeyEvent e) {
		// NOT IMPLEMENTED
	}

	private void runQuery() {
		Thread thread = new Thread(this);
		thread.start();
	}

	public void saveSqlNote() {
		String text = editor.getText();

		Node taskNode = taskEditor.getTaskNode();
		Node[] notes = taskNode.findNodes("Note", "name", panelName);

		String selectedConnection = (String) connectionsComboBox.getSelectedItem();
		Node noteToEdit = null;
		for (Node note : notes) {
			if (StringUtils.equals(note.getAttribute(CONNECTION_ATTR_NAME), selectedConnection)) {
				noteToEdit = note;
				break;
			}
		}

		if (noteToEdit == null) {
			noteToEdit = new Node("Note");
			noteToEdit.setAttribute("name", panelName);
			noteToEdit.setAttribute(CONNECTION_ATTR_NAME, selectedConnection);
			Node textNote = new Node(Node.TEXT_TEAG_NAME);
			textNote.setText("\n" + text + "\n");
			noteToEdit.add(textNote);
			taskNode.add(noteToEdit);
		} else {
			Node[] textNodes = noteToEdit.getTextNodes();
			if (textNodes.length > 0) {
				Node textNote = textNodes[0];
				textNote.setText("\n" + text + "\n");
			} else {
				Node textNote = new Node(Node.TEXT_TEAG_NAME);
				textNote.setText("\n" + text + "\n");
				noteToEdit.add(textNote);
			}
		}

		taskEditor.setTaskNode(taskNode);
		try {
			taskEditor.saveTask();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
					"File saving operation was unsuccessful. File: " + taskEditor.getTestFile());
		}
		taskEditor.reload();
	}

	public void setText(String text) {
		editor.setText(text);
	}

	public int getCaretPosition() {
		return editor.getCaretPosition();
	}

	public Point getMagicCaretPosition() {
		return editor.getCaret().getMagicCaretPosition();
	}

	public AEManager getManager() {
		return taskEditor.getManager();
	}

	public Processor getTaskProcessor() {
		return taskEditor.getTaskProcessor();
	}

	public String getText() {
		return editor.getText();
	}

	public void replaceRange(String text, int i, int caretPosition2) {
		editor.replaceRange(text, i, caretPosition2);
	}

	public void runTask() {
		run();
	}

	public void save() {
		saveSqlNote();
	}

	public void format() {
		// NOT IMPLEMENTED
	}

	public Editor getEditor() {
		return editor;
	}

	@Override
	public void reload() {
		// NOT IMPLEMENTED
	}

	@Override
	public DialogPopupMenu contextHelp(DialogPopupMenu menu) {
		return null;
	}

}