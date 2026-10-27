package cn.campus.resource.testing;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Each test class owns a fresh database. Docker is the default; localhost PostgreSQL is an explicit local option. */
public final class Member03Database implements AutoCloseable {
    private PostgreSQLContainer<?> container;
    private String adminUrl, database;
    private final DriverManagerDataSource source;
    private final String schema;
    public Member03Database(String schema) throws Exception {
        if(!schema.equals("classroom") && !schema.equals("maintenance")){throw new IllegalArgumentException("Unknown member03 schema");}
        this.schema=schema;
        adminUrl=System.getProperty("member03.postgres.admin-url");
        String url;
        if(adminUrl==null){
            container=DatabaseTestSupport.newContainer();container.start();url=DatabaseTestSupport.jdbcUrl(container,schema);
        }else{
            if(!adminUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres")){throw new IllegalArgumentException("Explicit native test server must be localhost /postgres");}
            database="campus_member03_test_"+UUID.randomUUID().toString().replace("-","");
            try(var c=DriverManager.getConnection(adminUrl,"postgres","");var q=c.createStatement()){
                q.execute("CREATE DATABASE "+database);
            }
            url=adminUrl.substring(0,adminUrl.lastIndexOf('/')+1)+database+"?currentSchema="+schema;
            try(var c=DriverManager.getConnection(url,"postgres","");var q=c.createStatement()){
                for(String suffix:new String[]{"app","migrator"}){
                    String role=schema+"_"+suffix;
                    q.execute("DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='"+role+"') THEN CREATE ROLE "+role+" LOGIN PASSWORD 'test-only-"+schema+"-"+(suffix.equals("app")?"application":"migration")+"-password'; END IF; END $$");
                }
                q.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
                q.execute("CREATE SCHEMA "+schema+" AUTHORIZATION "+schema+"_migrator");
                q.execute("GRANT USAGE ON SCHEMA "+schema+" TO "+schema+"_app");
                q.execute("ALTER DEFAULT PRIVILEGES FOR ROLE "+schema+"_migrator IN SCHEMA "+schema+" GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO "+schema+"_app");
                q.execute("ALTER DEFAULT PRIVILEGES FOR ROLE "+schema+"_migrator IN SCHEMA "+schema+" GRANT USAGE,SELECT ON SEQUENCES TO "+schema+"_app");
            }
        }
        Flyway.configure().dataSource(url,schema+"_migrator",DatabaseTestSupport.migratorPassword(schema))
            .defaultSchema(schema).schemas(schema).createSchemas(false).load().migrate();
        source=new DriverManagerDataSource(url,schema+"_app",DatabaseTestSupport.appPassword(schema));
    }
    public <T>T mapper(Class<T> type) throws Exception {
        var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);config.addMapper(type);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);
        return new SqlSessionTemplate(factory.getObject()).getMapper(type);
    }
    public JdbcTemplate jdbc(){return new JdbcTemplate(source);}
    public DataSourceTransactionManager manager(){return new DataSourceTransactionManager(source);}
    public void clear(){
        String tables=schema.equals("classroom")?"audit_outbox,request_release,approval_action,classroom_event,approval_step,classroom_request":"audit_outbox,ticket_review,ticket_event,repair_ticket";
        for(String table:tables.split(",")){jdbc().update("DELETE FROM "+schema+"."+table);}
    }
    @Override public void close() throws Exception {
        if(container!=null){container.stop();}
        else if(database!=null && database.startsWith("campus_member03_test_")){
            try(var c=DriverManager.getConnection(adminUrl,"postgres","");var q=c.createStatement()){q.execute("DROP DATABASE "+database+" WITH (FORCE)");}
        }
    }
}
