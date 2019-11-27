package org.meveo.persistence.sql;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.meveo.commons.utils.ParamBean;
import org.meveo.commons.utils.StringUtils;
import org.meveo.event.qualifier.Updated;
import org.meveo.jpa.EntityManagerWrapper;
import org.meveo.jpa.MeveoJpa;
import org.meveo.model.sql.SqlConfiguration;
import org.slf4j.Logger;

/**
 * @author Edward P. Legaspi | <czetsuya@gmail.com>
 * @version 6.6.0
 * @since 6.6.0
 */
@Startup
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class SQLConnectionProvider {

	@Inject
	@MeveoJpa
	private Provider<EntityManagerWrapper> emWrapperProvider;

	@Inject
	private Logger log;

	private String driverClass;
	private String url;
	private String username;
	private String password;
	private String dialect;

	private SqlConfiguration defaultSqlConfiguration = new SqlConfiguration();
	private static final Map<String, SqlConfiguration> configurationMap = new ConcurrentHashMap<>();
	private static final Map<String, SessionFactory> SESSION_FACTORY_MAP = new ConcurrentHashMap<>();

	@PostConstruct
	public void loadConfig() {

		driverClass = ParamBean.getInstance().getProperty("sql.driverClass", "org.postgresql.Driver");
		url = ParamBean.getInstance().getProperty("sql.url", "jdbc:postgresql://localhost/meveo");
		username = ParamBean.getInstance().getProperty("sql.username", "meveo");
		password = ParamBean.getInstance().getProperty("sql.password", "meveo");
		dialect = ParamBean.getInstance().getProperty("sql.dialect", "org.hibernate.dialect.PostgreSQLDialect");

		defaultSqlConfiguration.setCode("default");
		defaultSqlConfiguration.setDriverClass(driverClass);
		defaultSqlConfiguration.setUrl(url);
		defaultSqlConfiguration.setUsername(username);
		defaultSqlConfiguration.setPassword(password);
		defaultSqlConfiguration.setDialect(dialect);
	}

	public SqlConfiguration getSqlConfiguration(String sqlConfigurationCode) {

		SqlConfiguration sqlConfiguration = defaultSqlConfiguration;
		if (StringUtils.isNotBlank(sqlConfigurationCode)) {
			try {
				sqlConfiguration = configurationMap.computeIfAbsent(sqlConfigurationCode, this::findByCode);

			} catch (NoResultException e) {
				log.warn("Unknown SQL repository {}, using default configuration", sqlConfigurationCode);
			}
		}

		return sqlConfiguration;
	}

	public org.hibernate.Session getSession(String sqlConfigurationCode) {

		SqlConfiguration sqlConfiguration = getSqlConfiguration(sqlConfigurationCode);

		try {
			SessionFactory sessionFactory = SESSION_FACTORY_MAP.computeIfAbsent(sqlConfigurationCode, this::buildSessionFactory);
			synchronized (this) {
				return sessionFactory.openSession();
			}

		} catch (Exception e) {
			log.warn("Can't connect to sql configuration with code={}, url={}, error={}", sqlConfigurationCode, sqlConfiguration.getUrl(), e.getCause());
			return null;
		}
	}

	private synchronized SessionFactory buildSessionFactory(String sqlConfigurationCode) {

		SqlConfiguration sqlConfiguration = getSqlConfiguration(sqlConfigurationCode);

		Configuration config = new Configuration();
		config.setProperty("hibernate.connection.driver_class", sqlConfiguration.getDriverClass());
		config.setProperty("hibernate.connection.url", sqlConfiguration.getUrl());
		config.setProperty("hibernate.connection.username", sqlConfiguration.getUsername());
		config.setProperty("hibernate.connection.password", sqlConfiguration.getPassword());
		if (StringUtils.isNotBlank(sqlConfiguration.getDialect())) {
			config.setProperty("hibernate.dialect", sqlConfiguration.getDialect());
		}

		return config.buildSessionFactory();
	}

	public SqlConfiguration findByCode(String code) {

		EntityManager entityManager = emWrapperProvider.get().getEntityManager();
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();
		CriteriaQuery<SqlConfiguration> query = cb.createQuery(SqlConfiguration.class);
		Root<SqlConfiguration> root = query.from(SqlConfiguration.class);
		query.select(root);
		query.where(cb.equal(root.get("code"), code));

		return entityManager.createQuery(query).getSingleResult();
	}

	public void onSqlConnectionCreated(@Observes @Updated SqlConfiguration entity) {

		configurationMap.put(entity.getCode(), entity);

		SessionFactory oldSessionFactory = SESSION_FACTORY_MAP.get(entity.getCode());
		if (oldSessionFactory != null) {
			oldSessionFactory.close();
		}
		SESSION_FACTORY_MAP.put(entity.getCode(), buildSessionFactory(entity.getCode()));
	}

	public String getDriverClass() {
		return driverClass;
	}

	public void setDriverClass(String driverClass) {
		this.driverClass = driverClass;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDialect() {
		return dialect;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	public SqlConfiguration getDefaultSqlConfiguration() {
		return defaultSqlConfiguration;
	}

	public void setDefaultSqlConfiguration(SqlConfiguration defaultSqlConfiguration) {
		this.defaultSqlConfiguration = defaultSqlConfiguration;
	}
}
