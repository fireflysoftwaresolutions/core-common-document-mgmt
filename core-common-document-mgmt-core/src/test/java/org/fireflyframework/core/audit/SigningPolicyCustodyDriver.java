package org.fireflyframework.core.audit;
import java.time.Clock;
import java.util.*;
import org.fireflyframework.core.transaction.BoundedR2dbcResources;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;

/** Access to the pinned original Jafly networkless mapping fixture, not a database. */
public final class SigningPolicyCustodyDriver {
    final AuditTestFixture f=new AuditTestFixture();
    public Clock clock(){return f.clock;}
    public BoundedR2dbcResources resources(){return f.resources;}
    public DatabaseClient client(){return f.client;}
    public R2dbcEntityTemplate template(){return f.template;}
    public R2dbcTransactionManager manager(){return f.manager;}
    public int creates(){return f.driver.creates;}
    public int writes(){return f.driver.writes;}
    public int commits(){return f.driver.commits;}
    public int rollbacks(){return f.driver.rollbacks;}
    public List<Map<String,Object>> rows(String table){return f.driver.tables.getOrDefault(table,List.of());}
    public List<String> statements(){return List.copyOf(f.driver.sql);}
    public void seed(String table,Object value){var row=new LinkedHashMap<String,Object>();var outbound=f.template.getDataAccessStrategy().getOutboundRow(value);for(var column:outbound.keySet())row.put(column.getReference(),outbound.get(column).getValue());f.driver.tables.computeIfAbsent(table,key->new ArrayList<>()).add(row);}
    public void countAt(int write,long count){f.driver.countAtWrite=write;f.driver.writeCount=count;}
}
