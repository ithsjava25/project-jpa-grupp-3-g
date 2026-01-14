package org.example.config;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import jakarta.persistence.Entity;
import java.util.List;
import java.util.Map;

public class HibernateConfig {

    private static EntityManagerFactory emf;

    public static EntityManagerFactory getEntityManagerFactory(Map<String, String> extraProperties) {

        if (emf == null) {
            List<Class<?>> entities = getEntities("org.example.service");

            final PersistenceConfiguration cfg = new HibernatePersistenceConfiguration("emf")
                .jdbcUrl("jdbc:mysql://localhost:3306/restaurant_booking")
                .jdbcUsername("root")
                .jdbcPassword("root123")
                .property("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider")
                .property("hibernate.hikari.maximumPoolSize", "10")
                .property("hibernate.hikari.minimumIdle", "5")
                .property("hibernate.hikari.idleTimeout", "300000")
                .property("hibernate.hikari.connectionTimeout", "20000")
                .property("hibernate.hbm2ddl.auto", "update")
                .property("hibernate.show_sql", "true")
                .property("hibernate.format_sql", "true")
                .property("hibernate.highlight_sql", "true")
                .managedClasses(entities);

            if (extraProperties != null) {
                extraProperties.forEach(cfg::property);
            }

            emf = cfg.createEntityManagerFactory();
        }
        return emf;
    }

    public static List<Class<?>> getEntities(String pkg) {
        try (ScanResult scanResult = new ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages(pkg)
            .scan()) {
            return scanResult.getClassesWithAnnotation(Entity.class).loadClasses();
        }
    }
}
