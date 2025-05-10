package com.ganteater.ae.processor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.NamingException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.ganteater.ae.CommandException;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.processor.annotation.CommandHotHepl;
import com.ganteater.ae.util.AEUtils;
import com.ganteater.ae.util.xml.easyparser.Node;

public class SQL extends BaseProcessor {

	private Map<String, SQLQuery> connectionMap = new HashMap<>();
	private PLQuery plQuery;

	@Override
	public void init() throws CommandException {
		Node configNode = getParent().getConfigNode();
		LocalDataSource.createDBConnection(configNode, this);
	}

	@CommandExamples({
			"<Run type='enum:sql|pl/sql' description=''><var type='enum:input|output' name='type:property'/><code> ... </code></Run>",
			"<Run type='enum:sql|pl/sql' data='fields' description=''><var type='enum:input|output' name='type:property'/><code> ... </code></Run>",
			"<Run type='enum:sql|pl/sql' outputType='enum:CLOB|VARCHAR' description=''><var type='enum:input|output' name='type:property'/><code file='type:path'/></Run>}" })
	public void runCommandRun(final Node aCurrentAction) throws Throwable {
		final String theDataAttribut = aCurrentAction.getAttribute("data");
		if ("pl/sql".equals(aCurrentAction.getAttribute("type"))) {
			runPlSqlBlock(aCurrentAction);
		} else if ("sql".equals(aCurrentAction.getAttribute("type"))) {
			if ("fields".equals(theDataAttribut)) {
				runSqlFieldsBlock(aCurrentAction);
			} else {
				runSqlBlock(aCurrentAction);
			}
		}
	}

	@CommandExamples({ "<Sql connection=''>select a var_name1, b var_name2, ... from ... </Sql>" })
	@CommandHotHepl("<html>.</html>")
	public void runCommandSql(final Node aCurrentAction) throws Throwable {
		final String theText = replaceProperties(aCurrentAction.getInnerText());
		final String connection = StringUtils.defaultString(aCurrentAction.getAttribute("connection"),
				LocalDataSource.DEFAULT_CONNECTION_NAME);

		SQLQuery fSqlQuery = connection(connection);
		startCommandInformation(aCurrentAction);
		debug("SQL Query:  " + theText);

		final Map<String, ArrayList<Object>> outArray = new HashMap<String, ArrayList<Object>>();

		if (StringUtils.startsWithIgnoreCase(StringUtils.trim(theText), "select")) {
			final Properties[] properties = fSqlQuery.forArrayFields(theText);

			for (final Properties property : properties) {
				final Set<Object> keys = property.keySet();
				for (final Object key : keys) {
					final String name = (String) key;
					final Object object = property.get(name);
					ArrayList<Object> arrayList = outArray.get(name);
					if (arrayList == null) {
						arrayList = new ArrayList<Object>();
						outArray.put(name, arrayList);
					}
					arrayList.add(object);
				}

				final Set<String> keySet = outArray.keySet();
				for (final String arrayName : keySet) {
					final ArrayList<Object> list = outArray.get(arrayName);
					if (list.size() > 1) {
						setVariableValue(arrayName, list);

					} else {
						if (list.size() > 0) {
							setVariableValue(arrayName, list.get(0));
						}
					}
				}
			}
		} else {
			fSqlQuery.execute(theText);
		}

	}

	public void runSqlBlock(final Node aCurrentAction) throws Throwable {
		final Node[] theVarNodes = aCurrentAction.findNodes("var", "type", "output");
		final String connection = aCurrentAction.getAttribute("connection");

		final Node[] theRunNodes = aCurrentAction.getNodes("code");
		String theText = null;

		final ArrayList<ArrayList<String>> theData = new ArrayList<ArrayList<String>>();
		for (int j = 0; j < theVarNodes.length; j++) {
			theData.add(new ArrayList<String>());
		}

		for (int i = 0; i < theRunNodes.length; i++) {
			if (isStoppedTest()) {
				break;
			}
			SQLQuery fSqlQuery = connection(connection);
			try {
				theText = replaceProperties(theRunNodes[i].getInnerText());
				startCommandInformation(theRunNodes[i]);
				debug("Request:\n" + theText);

				theText = theText.trim();
				if (theVarNodes.length > 0) {
					Statement theStatement = null;
					try {
						theStatement = fSqlQuery.connection().createStatement();
						final ResultSet theResultSet = theStatement.executeQuery(theText);

						while (theResultSet != null && theResultSet.next() && isStoppedTest() == false) {
							for (int j = 0; j < theData.size(); j++) {
								final String theValue = fSqlQuery.getStringValue(theResultSet, j + 1);
								theData.get(j).add(theValue);
							}
						}
					} finally {
						if (theStatement != null) {
							theStatement.close();
						}
					}

					int j = 0;
					for (final ArrayList<?> theDataVector : theData) {
						Object theDataObject = null;
						if (theDataVector.size() > 1) {
							final String[] theDataObjectArray = new String[theDataVector.size()];
							for (int k = 0; k < theDataVector.size(); k++) {
								theDataObjectArray[k] = (String) theDataVector.get(k);
							}
							theDataObject = theDataObjectArray;

						} else if (theDataVector.size() == 1) {
							theDataObject = theDataVector.get(0);
						}

						setVariableValue(theVarNodes[j++].getAttribute("name"), theDataObject);
					}
				} else {
					if (theText.length() > 0) {
						fSqlQuery.execute(theText);
					}
				}

			} catch (final Exception e) {
				if (theText != null) {
					this.log.error("Sql query: " + theText);
				}
				log.error("Stack trace", e);
				throw e;
			} finally {
				if (fSqlQuery != null) {
					fSqlQuery.commit();
				}
			}
		}
	}

	protected SQLQuery connection(final String connection) throws SQLException, NamingException {
		SQLQuery tsqlQuery = connectionMap.get(connection);
		if (tsqlQuery == null) {
			tsqlQuery = new SQLQuery(connection);
			connectionMap.put(connection, tsqlQuery);
		}

		return tsqlQuery;
	}

	public void runSqlFieldsBlock(final Node aCurrentAction) throws Throwable {
		String thePropertyName = null;
		final Node[] theVarNodes = aCurrentAction.findNodes("var", "type", "output");
		final String connection = aCurrentAction.getAttribute("connection");

		if (theVarNodes != null && theVarNodes.length > 0) {
			thePropertyName = theVarNodes[0].getAttribute("name");
		}

		final Node[] theRunNodes = aCurrentAction.getNodes("code");
		String theText = null;
		for (int i = 0; i < theRunNodes.length; i++) {
			if (isStoppedTest()) {
				break;
			}

			SQLQuery fSqlQuery = connection(connection);
			try {
				theText = replaceProperties(theRunNodes[i].getInnerText());
				startCommandInformation(theRunNodes[i]);
				debug("Request:\n" + theText);
				final Properties[] theOutArrayList = fSqlQuery.forArrayFields(theText);

				final String[] theValueArray = new String[theOutArrayList.length];
				for (int l = 0; l < theOutArrayList.length; l++) {
					if (isStoppedTest()) {
						break;
					}
					theValueArray[l] = getFields(theOutArrayList[l]);
				}
				setVariableValue(thePropertyName, theValueArray);
			} catch (final Exception e) {
				if (theText != null) {
					this.log.error("Sql query: " + theText);
				}
				log.error("Stack trace", e);
				throw e;
			} finally {
				if (fSqlQuery != null) {
					fSqlQuery.commit();
				}
			}
		}
	}

	public void runPlSqlBlock(final Node aCurrentAction) throws Throwable {
		final String connection = aCurrentAction.getAttribute("connection");

		final ArrayList<Object> theVarArrayList = new ArrayList<>();
		final ArrayList<String> theOutVarArrayListName = new ArrayList<>();
		final Iterator<?> theVarIterator = aCurrentAction.iterator();
		while (theVarIterator.hasNext()) {
			final Node theCurrentVar = (Node) theVarIterator.next();
			if ("input".equals(theCurrentVar.getAttribute("type"))) {
				final String theAttribut = theCurrentVar.getAttribute("name");
				if (isActiveInitFor(theCurrentVar, "mandatory")) {
					runCommandVar(theCurrentVar);
				}
				theVarArrayList.add(getVariableValue(theAttribut));
			} else if ("output".equals(theCurrentVar.getAttribute("type"))) {
				final String theAttribut = theCurrentVar.getAttribute("name");
				theOutVarArrayListName.add(theAttribut);
				theVarArrayList.add(PLQuery.VARIABLE_OUT);
			}
		}
		final Node[] theRunNodes = aCurrentAction.getNodes("code");
		for (int i = 0; i < theRunNodes.length; i++) {
			if (isStoppedTest()) {
				break;
			}
			final Node[] theCodesNodes = theRunNodes[i].getTextNodes();
			String theCode = theRunNodes[i].getAttribute("file");
			if (theCode != null) {
				theCode = IOUtils.toString(AEUtils.getInputStream(theCode, getBaseDir()));
			}
			if (theCodesNodes != null && theCodesNodes.length > 0) {
				theCode = theCodesNodes[0].getText();
			}
			final String theText = replaceProperties(theCode);
			debug("Request:\n" + theText);
			final StringBuffer theVarList = new StringBuffer();
			for (int theCurrVar = 0; theCurrVar < theVarArrayList.size(); theCurrVar++) {
				if (isStoppedTest()) {
					break;
				}
				theVarList.append(theCurrVar + ". " + theVarArrayList.get(theCurrVar) + "\n");
			}
			debug("Variables:\n" + theVarList.toString());
			int theOutType = Types.VARCHAR;
			final String theOutTypeAttribut = aCurrentAction.getAttribute("outputType");
			if ("CLOB".equals(theOutTypeAttribut)) {
				theOutType = Types.CLOB;
			}

			if (this.plQuery == null) {
				this.plQuery = new PLQuery(connection);
			}
			if (this.plQuery != null) {
				final List<String> theOutArrayList = this.plQuery.executeBlock(theText, theVarArrayList, theOutType);
				if (this.plQuery != null) {
					this.plQuery.commit();
				}
				for (int theCurOutNum = 0; theCurOutNum < theOutVarArrayListName.size(); theCurOutNum++) {
					if (isStoppedTest()) {
						break;
					}
					if (theOutArrayList.get(theCurOutNum) != null) {
						final String theKey = theOutVarArrayListName.get(theCurOutNum);
						final String theValue = (String) theOutArrayList.get(theCurOutNum);
						setVariableValue(theKey, theValue);
					}
				}
			}
		}
	}

	public void runCommandJdbc(final Node aCurrentAction) throws Throwable {
		createDBConnection(aCurrentAction, variables);
	}

	public void createDBConnection(Node theDS, Map variables) {
		final String connection = StringUtils.defaultString(theDS.getAttribute("name"),
				LocalDataSource.DEFAULT_CONNECTION_NAME);
		Properties theProperties = new Properties();
		Map<String, String> theDbProperties = theDS.getAttributes();

		@SuppressWarnings("unchecked")
		Set<String> keys = theDbProperties.keySet();

		for (String theKey : keys) {
			String property = (String) theDbProperties.get(theKey);
			Map<String, Object> vars = new HashMap<String, Object>();
			vars.putAll(variables);
			String theValue = replaceProperties(property);
			theProperties.setProperty(theKey, theValue);
		}
		LocalDataSource.setConnectionProperties(connection, theProperties);
	}

}
