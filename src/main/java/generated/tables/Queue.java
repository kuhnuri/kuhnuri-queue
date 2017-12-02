/*
 * This file is generated by jOOQ.
*/
package generated.tables;


import generated.Keys;
import generated.Public;
import generated.enums.Status;
import generated.tables.records.QueueRecord;

import java.sql.Timestamp;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Identity;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.9.1"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Queue extends TableImpl<QueueRecord> {

    private static final long serialVersionUID = 85724513;

    /**
     * The reference instance of <code>public.queue</code>
     */
    public static final Queue QUEUE = new Queue();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<QueueRecord> getRecordType() {
        return QueueRecord.class;
    }

    /**
     * The column <code>public.queue.uuid</code>.
     */
    public final TableField<QueueRecord, String> UUID = createField("uuid", org.jooq.impl.SQLDataType.VARCHAR.length(256).nullable(false), this, "");

    /**
     * The column <code>public.queue.transtype</code>.
     */
    public final TableField<QueueRecord, String> TRANSTYPE = createField("transtype", org.jooq.impl.SQLDataType.VARCHAR.length(256).nullable(false), this, "");

    /**
     * The column <code>public.queue.created</code>.
     */
    public final TableField<QueueRecord, Timestamp> CREATED = createField("created", org.jooq.impl.SQLDataType.TIMESTAMP.nullable(false), this, "");

    /**
     * The column <code>public.queue.input</code>.
     */
    public final TableField<QueueRecord, String> INPUT = createField("input", org.jooq.impl.SQLDataType.VARCHAR.length(256).nullable(false), this, "");

    /**
     * The column <code>public.queue.output</code>.
     */
    public final TableField<QueueRecord, String> OUTPUT = createField("output", org.jooq.impl.SQLDataType.VARCHAR.length(256).nullable(false), this, "");

    /**
     * The column <code>public.queue.status</code>.
     */
    public final TableField<QueueRecord, Status> STATUS = createField("status", org.jooq.util.postgres.PostgresDataType.VARCHAR.asEnumDataType(generated.enums.Status.class), this, "");

    /**
     * The column <code>public.queue.id</code>.
     */
    public final TableField<QueueRecord, Integer> ID = createField("id", org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaultValue(org.jooq.impl.DSL.field("nextval('queue_id_seq'::regclass)", org.jooq.impl.SQLDataType.INTEGER)), this, "");

    /**
     * The column <code>public.queue.processing</code>.
     */
    public final TableField<QueueRecord, Timestamp> PROCESSING = createField("processing", org.jooq.impl.SQLDataType.TIMESTAMP, this, "");

    /**
     * The column <code>public.queue.finished</code>.
     */
    public final TableField<QueueRecord, Timestamp> FINISHED = createField("finished", org.jooq.impl.SQLDataType.TIMESTAMP, this, "");

    /**
     * The column <code>public.queue.priority</code>.
     */
    public final TableField<QueueRecord, Integer> PRIORITY = createField("priority", org.jooq.impl.SQLDataType.INTEGER.defaultValue(org.jooq.impl.DSL.field("0", org.jooq.impl.SQLDataType.INTEGER)), this, "");

    /**
     * Create a <code>public.queue</code> table reference
     */
    public Queue() {
        this("queue", null);
    }

    /**
     * Create an aliased <code>public.queue</code> table reference
     */
    public Queue(String alias) {
        this(alias, QUEUE);
    }

    private Queue(String alias, Table<QueueRecord> aliased) {
        this(alias, aliased, null);
    }

    private Queue(String alias, Table<QueueRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Identity<QueueRecord, Integer> getIdentity() {
        return Keys.IDENTITY_QUEUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Queue as(String alias) {
        return new Queue(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public Queue rename(String name) {
        return new Queue(name, null);
    }
}
