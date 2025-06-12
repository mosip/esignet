package in.gov.uidai.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ConditionalOnProperty(value = "mosip.esignet.dao.enabled", matchIfMissing = true)
@Configuration
@EnableJpaRepositories(
        basePackages = {"in.gov.uidai.repositories"},
        entityManagerFactoryRef = "uidaiEntityManagerFactory",
        transactionManagerRef = "uidaiTransactionManager")
public class UidaiDaoConfig {

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
    private static final String POSTGRESQL_95_DIALECT = "org.hibernate.dialect.PostgreSQL95Dialect";

    @Autowired
    private Environment environment;

    @Value("${uidai.jdbc.schema:mockidentitysystem}")
    private String schema;
    @Value("${uidai.hikari.maximumPoolSize:25}")
    private int maximumPoolSize;
    @Value("${uidai.hikari.validationTimeout:3000}")
    private int validationTimeout;
    @Value("${uidai.hikari.connectionTimeout:60000}")
    private int connectionTimeout;
    @Value("${uidai.hikari.idleTimeout:200000}")
    private int idleTimeout;
    @Value("${uidai.hikari.minimumIdle:0}")
    private int minimumIdle;


    @Bean
    public DataSource uidaiDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(environment.getProperty("uidai.database.driver"));
        hikariConfig.setJdbcUrl(environment.getProperty("uidai.database.url"));
        hikariConfig.setUsername(environment.getProperty("uidai.database.username"));
        hikariConfig.setPassword(environment.getProperty("uidai.database.password"));
        hikariConfig.setSchema(schema);
        hikariConfig.setMaximumPoolSize(maximumPoolSize);
        hikariConfig.setValidationTimeout(validationTimeout);
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setIdleTimeout(idleTimeout);
        hikariConfig.setMinimumIdle(minimumIdle);
        return new HikariDataSource(hikariConfig);
    }


    @Bean
    public LocalContainerEntityManagerFactoryBean uidaiEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(uidaiDataSource());
        entityManagerFactory.setPackagesToScan(new String []{"in.gov.uidai.entities"});
        entityManagerFactory.setPersistenceUnitName("hibernate");
        entityManagerFactory.setJpaPropertyMap(uidaiJpaProperties());
        entityManagerFactory.setJpaVendorAdapter(uidaiJpaVendorAdapter());
        entityManagerFactory.setJpaDialect(uidaiJpaDialect());
        return entityManagerFactory;
    }



    @Bean
    public JpaVendorAdapter uidaiJpaVendorAdapter() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(true);
        return vendorAdapter;
    }



    @Bean
    public JpaDialect uidaiJpaDialect() {
        return new HibernateJpaDialect();
    }



    @Bean
    public PlatformTransactionManager uidaiTransactionManager() {
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(uidaiEntityManagerFactory().getObject());
        jpaTransactionManager.setDataSource(uidaiDataSource());
        jpaTransactionManager.setJpaDialect(uidaiJpaDialect());
        return jpaTransactionManager;
    }


    public Map<String, Object> uidaiJpaProperties() {
        HashMap<String, Object> jpaProperties = new HashMap<>();
        getProperty(jpaProperties, HIBERNATE_HBM2DDL_AUTO, NONE);
        getProperty(jpaProperties, HIBERNATE_DIALECT, POSTGRESQL_95_DIALECT);
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



    private HashMap<String, Object> getProperty(HashMap<String, Object> jpaProperties, String property,
                                                String defaultValue) {
        if (property.equals(HIBERNATE_EJB_INTERCEPTOR)) {
            try {
                if (environment.containsProperty(property)) {
                    jpaProperties.put(property,
                            // encryptionInterceptor());
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
