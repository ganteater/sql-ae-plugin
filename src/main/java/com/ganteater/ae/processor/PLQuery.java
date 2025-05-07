package com.ganteater.ae.processor;

import java.io.IOException;
import java.io.Reader;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.naming.NamingException;

import org.apache.commons.lang.StringUtils;

public class PLQuery extends BaseConnectionProvider {

	protected Statement fStatement = null;
	protected String fQueryLine;
	public static final String VARIABLE_OUT = "${VARIABLE_OUT}";

	public PLQuery(String connectionName) throws SQLException, NamingException {
		super(connectionName);
		setAutoCommit(false);
	}

	protected void execute(String aQuery) throws SQLException {
		super.startPoint();

		try {
			fStatement = connection().createStatement();
			fStatement.execute(aQuery);
		} finally {
			try {
				fStatement.close();
			} catch (Exception e) {
			}
			super.finishPoint();
		}
	}

	public void executeProcedure(String fFunctionName, Properties aParameters) throws SQLException {
		String theParametersLine = StringUtils.EMPTY;
		if (aParameters != null)
			theParametersLine = getParametersLine(aParameters);
		fQueryLine = "begin " + fFunctionName + "(" + theParametersLine + "); end;";
		execute(fQueryLine);
	}

	private String getParametersLine(Properties aProperties) {
		StringBuilder theBuffer = new StringBuilder();
		int i = 0;

		Enumeration<Object> enumeration = aProperties.keys();
		while (enumeration.hasMoreElements()) {
			String theKey = (String) enumeration.nextElement();
			if (i++ > 0)
				theBuffer.append(',');
			theBuffer.append("");
			theBuffer.append(theKey);
			theBuffer.append("=>");
			theBuffer.append(aProperties.getProperty(theKey));
		}

		return theBuffer.toString();
	}

	@Override
	public String getQueryLine() {
		return fQueryLine;
	}

	public List<String> executeBlock(String runCode, List<Object> variables, int outputType) throws SQLException {

		List<String> result = new ArrayList<>();
		runCode = runCode.replace('\r', ' ');

		List<Integer> outVarId = new ArrayList<>();

		CallableStatement theCallableStatement = null;
		try {
			theCallableStatement = connection().prepareCall(runCode);
			for (int i = 0; i < variables.size(); i++) {
				Object theValue = variables.get(i);

				if (!VARIABLE_OUT.equals(theValue)) {
					if (theValue == null) {
						theCallableStatement.setNull(i + 1, Types.VARCHAR);
					} else {
						if (theValue instanceof String) {
							theCallableStatement.setString(i + 1, (String) theValue);
						} else if (theValue instanceof byte[]) {
							theCallableStatement.setBytes(i + 1, (byte[]) theValue);
						}
					}
				} else {
					theCallableStatement.registerOutParameter(i + 1, outputType);
					outVarId.add(i + 1);
				}
			}

			theCallableStatement.execute();

			for (int j = 0; j < outVarId.size(); j++) {
				int theIndex = (outVarId.get(j)).intValue();

				if (outputType == Types.VARCHAR) {
					result.add(theCallableStatement.getString(theIndex));
				}

				if (outputType == Types.CLOB) {
					Clob theClob = theCallableStatement.getClob(theIndex);
					Reader theReader = theClob.getCharacterStream();
					StringBuilder theBuffer = new StringBuilder();

					int theChar = 0;
					try {
						while ((theChar = theReader.read()) >= 0) {
							theBuffer.append((char) theChar);
						}
					} catch (IOException e) {
						theBuffer.append(e.getMessage());
					}

					result.add(theBuffer.toString());
				}
			}

		} finally {
			if (theCallableStatement != null)
				theCallableStatement.close();
		}

		return result;
	}
}
