package com.ganteater.ae.processor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;

public class SQLQuery extends BaseConnectionProvider {
	protected Statement fStatement = null;
	protected String fQueryLine;

	public SQLQuery(String connectionName) throws SQLException, NamingException {
		super(connectionName);
		fStatement = connection().createStatement();
	}

	public void execute(String aQuery) throws SQLException {
		fQueryLine = aQuery;
		startPoint();
		fStatement.execute(aQuery);
		finishPoint();
	}

	public Properties forFields(String aQuery) throws SQLException {
		fQueryLine = aQuery;
		startPoint();

		Properties theProperties = new Properties();
		ResultSet resultSet = fStatement.executeQuery(aQuery);

		try {
			resultSet = fStatement.executeQuery(aQuery);
			if (resultSet.next())
				theProperties = getAllFields(resultSet);
		} finally {
			try {
				resultSet.close();
			} catch (Exception e) {
			}
		}

		finishPoint();
		return theProperties;
	}

	public Properties[] forArrayFields(String aQuery) throws SQLException {
		fQueryLine = aQuery;
		ArrayList<Properties> theArrayList = new ArrayList<>();

		startPoint();
		ResultSet resultSet = null;

		try {
			resultSet = fStatement.executeQuery(aQuery);

			while (resultSet.next()) {
				Properties theProperties = getAllFields(resultSet);
				theArrayList.add(theProperties);
			}

		} finally {
			try {
				if (resultSet != null)
					resultSet.close();
			} catch (Exception e) {
			}
		}

		Properties[] theProperties = new Properties[theArrayList.size()];
		for (int i = 0; i < theProperties.length; i++)
			theProperties[i] = (Properties) theArrayList.get(i);
		finishPoint();

		return theProperties;
	}

	public ResultSet forResultSet(String aQuery) throws SQLException {
		fQueryLine = aQuery;
		startPoint();
		ResultSet resultSet = null;
		resultSet = fStatement.executeQuery(aQuery);

		return resultSet;
	}

	private Properties getAllFields(ResultSet aResultSet) throws SQLException {
		Properties theProperties = new Properties();

		int theColumnCount = aResultSet.getMetaData().getColumnCount();
		for (int theColumn = 1; theColumn < theColumnCount + 1; theColumn++) {
			String theResult = getStringValue(aResultSet, theColumn);
			String theColumnName = aResultSet.getMetaData().getColumnName(theColumn);
			if (theResult != null)
				theProperties.setProperty(theColumnName, theResult);
		}

		return theProperties;
	}

	public Properties forPairsFields(String aQuery) throws SQLException {
		Properties theProperties = new Properties();
		fQueryLine = aQuery;

		startPoint();

		ResultSet resultSet = fStatement.executeQuery(aQuery);

		try {
			resultSet = fStatement.executeQuery(aQuery);

			while (resultSet.next()) {
				String theValue = resultSet.getString(1);
				theProperties.setProperty(theValue, resultSet.getString(2));
			}

		} finally {
			try {
				resultSet.close();
			} catch (Exception e) {
			}
		}

		finishPoint();
		return theProperties;
	}

	public List<String> forArrayValues(String aQuery) throws SQLException {
		fQueryLine = aQuery;

		startPoint();

		ResultSet resultSet = null;
		ArrayList<String> theArrayList = new ArrayList<>();

		try {
			resultSet = fStatement.executeQuery(aQuery);

			while (resultSet != null && resultSet.next()) {
				String theValue = getStringValue(resultSet, 1);
				theArrayList.add(theValue);
			}

		} finally {
			try {
				if (resultSet != null)
					resultSet.close();
			} catch (Exception e) {
			}
		}

		finishPoint();

		return theArrayList;
	}

	public void close() {
		try {
			fStatement.close();
		} catch (Exception e) {
		}

		super.close();
	}

}
