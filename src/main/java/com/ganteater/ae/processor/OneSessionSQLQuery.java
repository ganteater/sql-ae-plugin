package com.ganteater.ae.processor;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;

public class OneSessionSQLQuery {

	public static List<String> forArrayValues(String aQueryLine, String connectionName)
			throws SQLException, NamingException {
		SQLQuery theQuery = null;
		List<String> theStrings = null;
		try {
			theQuery = new SQLQuery(connectionName);
			theStrings = theQuery.forArrayValues(aQueryLine);
		} finally {
			if (theQuery != null)
				theQuery.close();
		}
		return theStrings;
	}

	public static Properties forFields(String aQueryLine, String connectionName) throws SQLException, NamingException {
		SQLQuery theQuery = null;
		Properties theProperties = null;
		try {
			theQuery = new SQLQuery(connectionName);
			theProperties = theQuery.forFields(aQueryLine);
		} finally {
			if (theQuery != null)
				theQuery.close();
		}
		return theProperties;
	}

	public static Properties[] forArrayFields(String aQueryLine, String connectionName)
			throws SQLException, NamingException {
		SQLQuery theQuery = null;
		Properties[] theProperties = null;
		try {
			theQuery = new SQLQuery(connectionName);
			theProperties = theQuery.forArrayFields(aQueryLine);
		} finally {
			if (theQuery != null)
				theQuery.close();
		}
		return theProperties;
	}

}
