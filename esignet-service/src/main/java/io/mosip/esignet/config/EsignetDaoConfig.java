package io.mosip.esignet.config;


import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.mosip.kernel.keymanagerservice.constant.HibernatePersistenceConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaDialect;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;


@Slf4j
@ConditionalOnProperty(value = "mosip.esignet.dao.enabled", matchIfMissing = true)
@Configuration
@EnableJpaRepositories(
        basePackages = {"io.mosip.esignet.repository", "io.mosip.kernel.keymanagerservice.repository"},
        entityManagerFactoryRef = "esignetEntityManagerFactory",
        transactionManagerRef = "esignetTransactionManager")
public class EsignetDaoConfig {

    private static final String HIBERNATE_GENERATE_STATISTICS = "hibernate.generate_statistics";
    private static final String HIBERNATE_CACHE_USE_STRUCTURED_ENTRIES = "hibernate.cache.use_structured_entries";
    private static final String HIBERNATE_CACHE_USE_QUERY_CACHE = "hibernate.cache.use_query_cache";
    private static final String HIBERNATE_CACHE_USE_SECOND_LEVEL_CACHE = "hibernate.cache.use_second_level_cache";
    private static final String HIBERNATE_CONNECTION_CHAR_SET = "hibernate.connection.charSet";
    private static final String HIBERNATE_FORMAT_SQL = "hibernate.format_sql";
    private static final String HIBERNATE_SHOW_SQL = "hibernate.show_sql";
    private static final String HIBERNATE_DIALECT = "hibernate.dialect";
    private static final String HIBERNATE_HBM2DDL_AUTO = "hibernate.hbm2ddl.auto";
    private static final String HIBERNATE_NON_CONTEXTUAL_CREATION = "hibernate.jdbc.lob.non_contextual_creation";
    private static final String HIBERNATE_CURRENT_SESSION_CONTEXT = "hibernate.current_session_context_class";
    private static final String FALSE = "false";
    private static final String UTF8 = "utf8";
    private static final String TRUE = "true";
    private static final String NONE = "none";
    private static final String JTA = "jta";
    private static final String HIBERNATE = "hibernate";
    private static final String HIBERNATE_EJB_INTERCEPTOR = "hibernate.ejb.interceptor";
    private static final String EMPTY_INTERCEPTOR = "hibernate.empty.interceptor";

    @Autowired
    private Environment environment;

    @Value("${mosip.esignet.jdbc.schema:esignet}")
    private String schema;
    @Value("${mosip.esignet.hikari.maximum.pool.size:25}")
    private int maximumPoolSize;
    @Value("${mosip.esignet.hikari.validation.timeout:3000}")
    private int validationTimeout;
    @Value("${mosip.esignet.hikari.connection.timeout:60000}")
    private int connectionTimeout;
    @Value("${mosip.esignet.hikari.idle.timeout:200000}")
    private int idleTimeout;
    @Value("${mosip.esignet.hikari.minimum.idle:0}")
    private int minimumIdle;
    @Value("${mosip.esignet.jdbc.show.sql:false}")
    private boolean showSQL;
    @Value("${mosip.esignet.jdbc.generate.ddl:false}")
    private boolean generateDDL;

    @Primary
    @Bean
    public DataSource esignetDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(environment.getProperty("mosip.esignet.database.url"));
        hikariConfig.setUsername(environment.getProperty("mosip.esignet.database.username"));
        hikariConfig.setPassword(environment.getProperty("mosip.esignet.database.password"));
        if(!StringUtils.isEmpty(schema)) {
            hikariConfig.setSchema(schema);
        }
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setValidationTimeout(validationTimeout);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setIdleTimeout(idleTimeout);
        hikariConfig.setMinimumIdle(minimumIdle);

        hikariConfig.addDataSourceProperty("stringtype", "unspecified");

        return new HikariDataSource(hikariConfig);
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean esignetEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(esignetDataSource());
        entityManagerFactory.setPackagesToScan(new String []
                {"io.mosip.esignet.entity", "io.mosip.kernel.keymanagerservice.entity"});
        entityManagerFactory.setPersistenceUnitName(HibernatePersistenceConstant.HIBERNATE);
        entityManagerFactory.setJpaPropertyMap(esignetJpaProperties());
        entityManagerFactory.setJpaVendorAdapter(esignetJpaVendorAdapter());
        entityManagerFactory.setJpaDialect(esignetJpaDialect());
        return entityManagerFactory;
    }


    @Primary
    @Bean
    public JpaVendorAdapter esignetJpaVendorAdapter() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(generateDDL);
        vendorAdapter.setShowSql(showSQL);
        return vendorAdapter;
    }


    @Primary
    @Bean
    public JpaDialect esignetJpaDialect() {
        return new HibernateJpaDialect();
    }


    @Primary
    @Bean
    public PlatformTransactionManager esignetTransactionManager() {
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(esignetEntityManagerFactory().getObject());
        jpaTransactionManager.setDataSource(esignetDataSource());
        jpaTransactionManager.setJpaDialect(esignetJpaDialect());
        return jpaTransactionManager;
    }


    public Map<String, Object> esignetJpaProperties() {
        HashMap<String, Object> jpaProperties = new HashMap<>();
        getProperty(jpaProperties, HIBERNATE_HBM2DDL_AUTO, NONE);
        getProperty(jpaProperties, HIBERNATE_SHOW_SQL, FALSE);
        getProperty(jpaProperties, HIBERNATE_FORMAT_SQL, FALSE);
        getProperty(jpaProperties, HIBERNATE_CONNECTION_CHAR_SET, UTF8);
        getProperty(jpaProperties, HIBERNATE_CACHE_USE_SECOND_LEVEL_CACHE, FALSE);
        getProperty(jpaProperties, HIBERNATE_CACHE_USE_QUERY_CACHE, FALSE);
        getProperty(jpaProperties, HIBERNATE_CACHE_USE_STRUCTURED_ENTRIES, FALSE);
        getProperty(jpaProperties, HIBERNATE_GENERATE_STATISTICS, FALSE);
        getProperty(jpaProperties, HIBERNATE_NON_CONTEXTUAL_CREATION, FALSE);
        getProperty(jpaProperties, HIBERNATE_CURRENT_SESSION_CONTEXT, JTA);
        getProperty(jpaProperties, HIBERNATE_EJB_INTERCEPTOR, EMPTY_INTERCEPTOR);
        return jpaProperties;
    }


    /**
     * Function to associate the specified value with the specified key in the map.
     * If the map previously contained a mapping for the key, the old value is
     * replaced.
     *
     * @param jpaProperties The map of jpa properties
     * @param property      The property whose value is to be set
     * @param defaultValue  The default value to set
     * @return The map of jpa properties with properties set
     */
    private HashMap<String, Object> getProperty(HashMap<String, Object> jpaProperties, String property,
                                                String defaultValue) {
        /**
         * if property found in properties file then add that interceptor to the jpa
         * properties.
         */
        if (property.equals(HIBERNATE_EJB_INTERCEPTOR)) {
            try {
                if (environment.containsProperty(property)) {
                    jpaProperties.put(property,
                            BeanUtils.instantiateClass(Class.forName(environment.getProperty(property))));
                }
                /**
                 * We can add a default interceptor whenever we require here.
                 */
            } catch (BeanInstantiationException | ClassNotFoundException e) {
                log.error("Error while configuring Interceptor.");
            }
        } else {
            jpaProperties.put(property,
                    environment.containsProperty(property) ? environment.getProperty(property) : defaultValue);
        }
        return jpaProperties;
    }
}
