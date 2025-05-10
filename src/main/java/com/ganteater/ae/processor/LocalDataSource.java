package com.ganteater.ae.processor;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.ganteater.ae.TemplateProcessor;
import com.ganteater.ae.util.xml.easyparser.Node;

public class LocalDataSource implements DataSource {

	public static final String DEFAULT_CONNECTION_NAME = "[main]";

	private static final Logger log = Logger.getLogger(LocalDataSource.class);

	private static Map<String, Properties> fProperties = new HashMap<>();

	private String connectionName;

	public LocalDataSource(String connectionName) {
		this.connectionName = connectionName;
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return 0;
	}

	@Override
	public void setLoginTimeout(final int seconds) throws SQLException {
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return null;
	}

	@Override
	public void setLogWriter(final PrintWriter out) throws SQLException {
	}

	@Override
	public Connection getConnection() throws SQLException {
		String connectionName = StringUtils.defaultIfEmpty(this.connectionName, DEFAULT_CONNECTION_NAME);
		Properties properties = fProperties.get(connectionName);
		if (properties == null) {
			throw new SQLException("JDBC connection: '" + connectionName + "' is not defined.");
		}
		Connection connection = getConnection(properties.getProperty("username"), properties.getProperty("password"));

		boolean autoCommit = Boolean.valueOf(properties.getProperty("autoCommit"));
		connection.setAutoCommit(autoCommit);

		return connection;
	}

	public Connection getConnection(final String username, final String password) throws SQLException {
		Connection connection = null;
		String connectionName = StringUtils.defaultIfEmpty(this.connectionName, DEFAULT_CONNECTION_NAME);
		Properties properties = fProperties.get(connectionName);
		String url = properties.getProperty("url");
		connection = DriverManager.getConnection(url, username, password);

		SQLWarning warning = connection.getWarnings();
		while (null != warning) {
			String message = warning.getMessage();
			log.warn(message);
			warning = warning.getNextWarning();
		}

		return connection;
	}

	public static void setConnectionProperties(String connectionName, final Properties aProperties) {
		connectionName = StringUtils.defaultIfEmpty(connectionName, DEFAULT_CONNECTION_NAME);

		String driverName = aProperties.getProperty("driver");
		String url = aProperties.getProperty("url");

		fProperties.put(connectionName, aProperties);

		log.debug("Connection [" + connectionName + "] to:");
		log.debug("Driver: " + driverName);
		log.debug("URL: " + url);
		log.debug("Username: " + aProperties.getProperty("username"));
		String password = aProperties.getProperty("password");
		if (password != null) {
			password = StringUtils.leftPad("", password.length(), "*");
		}
		log.debug("Password: " + password);

		try {
			Driver driver = (Driver) Class.forName(driverName).getDeclaredConstructor().newInstance();
			DriverManager.registerDriver(driver);

		} catch (final Exception ex) {
			throw new RuntimeException("Cannot load/register jdbc driver: " + driverName + ": " + ex.getMessage());
		}
	}

	public <T> T unwrap(final Class<T> iface) throws SQLException {
		return null;
	}

	public boolean isWrapperFor(final Class<?> iface) throws SQLException {
		return false;
	}

	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return null;
	}

	public static Set<String> getConnectionNames() {
		return fProperties.keySet();
	}

	public static void createDBConnection(Node taskNode, Processor processor) {
		if (taskNode != null) {
			Node[] connections = taskNode.findNodes("Var", "type", "jdbc");
			for (Node propNodes : connections) {
				String connection = propNodes.getAttribute("name");
				Properties properties = new Properties();

				Node[] nodes = propNodes.getNodes("item");
				if (nodes.length > 0) {
					for (Node node : nodes) {
						String key = node.getAttribute("key");
						String value = node.getAttribute("value");
						if (value == null) {
							value = node.getText();
						}

						Map<String, Object> variables = processor.getVariables();
						value = new TemplateProcessor(variables, taskNode, processor.getBaseDir())
								.replaceProperties(value);
						properties.setProperty(key, value);
					}
				}

				LocalDataSource.setConnectionProperties(connection, properties);
			}
		}
	}
}
