package com.ganteater.ae.processor;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.commons.io.IOUtils;

import oracle.jdbc.OracleTypes;

public class BaseConnectionProvider extends AbstractConnectionProvider {

	public BaseConnectionProvider(String connectionName) throws SQLException {
		super(connectionName);
	}

	protected String fQueryLine;

	private long fStartTime;
	private long fEndTime;

	public String getQueryLine() {
		return fQueryLine;
	}

	public long getExecuteTime() {
		if (fStartTime == 0 || fEndTime == 0)
			return -1;
		return fEndTime - fStartTime;
	}

	protected void startPoint() {
		fStartTime = System.currentTimeMillis();
	}

	protected void finishPoint() {
		fEndTime = System.currentTimeMillis();
	}

	public String getStringValue(ResultSet aResultSet, int aColumn) throws SQLException {

		String theResult = null;
		int aOracleType = aResultSet.getMetaData().getColumnType(aColumn);

		switch (aOracleType) {

		case OracleTypes.BIT:
		case OracleTypes.BINARY:
			theResult = Byte.toString(aResultSet.getByte(aColumn));
			break;

		case OracleTypes.INTEGER:
		case OracleTypes.SMALLINT:
		case OracleTypes.TINYINT:
			theResult = Integer.toString(aResultSet.getInt(aColumn));
			break;

		case OracleTypes.DECIMAL:
			theResult = Integer.toString(aResultSet.getInt(aColumn));
			break;

		case OracleTypes.NUMERIC:
			BigDecimal theBigDecimal = aResultSet.getBigDecimal(aColumn);
			if (theBigDecimal != null)
				theResult = theBigDecimal.toString();
			break;

		case OracleTypes.BIGINT:
			theResult = aResultSet.getBigDecimal(aColumn).toString();
			break;

		case OracleTypes.FLOAT:
			theResult = Float.toString(aResultSet.getFloat(aColumn));
			break;

		case OracleTypes.REAL:
			theResult = Double.toString(aResultSet.getDouble(aColumn));
			break;

		case OracleTypes.DOUBLE:
			theResult = Double.toString(aResultSet.getDouble(aColumn));
			break;

		case OracleTypes.NVARCHAR:
			theResult = aResultSet.getNString(aColumn);
			break;

		case OracleTypes.CHAR:
		case OracleTypes.VARCHAR:
			theResult = aResultSet.getString(aColumn);
			break;

		case OracleTypes.LONGVARCHAR:
			break;

		case OracleTypes.DATE:
			Date theDate = aResultSet.getDate(aColumn);
			if (theDate != null)
				theResult = theDate.toString();
			break;

		case OracleTypes.TIME:
			theResult = aResultSet.getTime(aColumn).toString();
			break;

		case OracleTypes.TIMESTAMPLTZ:
		case OracleTypes.TIMESTAMPTZ:
		case OracleTypes.TIMESTAMP:
			Timestamp timestamp = aResultSet.getTimestamp(aColumn);
			if (timestamp != null)
				theResult = timestamp.toString();
			break;

		case OracleTypes.VARBINARY:
			break;

		case OracleTypes.LONGVARBINARY:
			break;

		case OracleTypes.ROWID:
			theResult = Long.toString(aResultSet.getLong(aColumn));
			break;

		case OracleTypes.CURSOR:
			break;

		case OracleTypes.BLOB:
			Blob blob = aResultSet.getBlob(aColumn);
			try {
				if (blob != null) {
					theResult = new String(IOUtils.toByteArray(blob.getBinaryStream()));
				}
			} catch (IOException e) {
				new SQLException(e);
			}
			break;

		case OracleTypes.CLOB:
			Clob clob = aResultSet.getClob(aColumn);
			try {
				if (clob != null) {
					theResult = IOUtils.toString(clob.getAsciiStream());
				}
			} catch (IOException e) {
				new SQLException(e);
			}
			break;

		case OracleTypes.BFILE:
			break;

		case OracleTypes.STRUCT:
			break;

		case OracleTypes.ARRAY:
			break;

		case OracleTypes.REF:
			break;

		case OracleTypes.OPAQUE:
			break;

		case OracleTypes.JAVA_STRUCT:
			break;

		case OracleTypes.JAVA_OBJECT:
			break;

		case OracleTypes.PLSQL_INDEX_TABLE:
			break;

		case OracleTypes.NULL:
			break;

		case OracleTypes.OTHER:
			theResult = aResultSet.getString(aColumn);
			break;

		case OracleTypes.FIXED_CHAR:
			break;

		case OracleTypes.DATALINK:
			break;

		case OracleTypes.BOOLEAN:
			theResult = Boolean.toString(aResultSet.getBoolean(aColumn));
			break;
		}
		return theResult;
	}

}
