package com.ganteater.ae.processor;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.ganteater.ae.ILogger;
import com.ganteater.ae.Logger;

/**
 * This abstract class for getting connect to DB in JNDI and Local part.
 */
public abstract class AbstractConnectionProvider {

	private static final ILogger log = new Logger("AbstractConnectionProvider");
	private Connection fConnection = null;

	public boolean isLogRaport() {
		return fLogRaport;
	}

	public void setLogRaport(boolean aLogRaport) {
		fLogRaport = aLogRaport;
	}

	private boolean fLogRaport = false;

	protected AbstractConnectionProvider(String connectionName) throws SQLException {
		DataSource fDataSource = new LocalDataSource(connectionName);
		fConnection = fDataSource.getConnection();
	}

	public void setAutoCommit(boolean aAutoCommit) throws SQLException {
		fConnection.setAutoCommit(aAutoCommit);
	}

	public void close() {
		try {
			if (!fConnection.isClosed()) {
				fConnection.close();
			}
		} catch (Exception e) {
			log.error("Exception in close block: ", e);
		}
	}

	public Connection connection() {
		return fConnection;
	}

	public void commit() throws SQLException {
		fConnection.commit();
	}

	public void rollback() throws SQLException {
		fConnection.rollback();
	}
}
