/*
 * This file is generated by jOOQ.
*/
package generated;


import generated.tables.Job;
import generated.tables.Task;

import javax.annotation.Generated;

import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.Internal;


/**
 * A class modelling indexes of tables of the <code>public</code> schema.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.10.5"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index JOB_ID_PK = Indexes0.JOB_ID_PK;
    public static final Index JOB_ID_UINDEX = Indexes0.JOB_ID_UINDEX;
    public static final Index JOB_UUID_UINDEX = Indexes0.JOB_UUID_UINDEX;
    public static final Index TASK_ID_PK = Indexes0.TASK_ID_PK;
    public static final Index TASK_ID_UINDEX = Indexes0.TASK_ID_UINDEX;
    public static final Index TASK_JOB_INDEX = Indexes0.TASK_JOB_INDEX;
    public static final Index TASK_UUID_UINDEX = Indexes0.TASK_UUID_UINDEX;

    // -------------------------------------------------------------------------
    // [#1459] distribute members to avoid static initialisers > 64kb
    // -------------------------------------------------------------------------

    private static class Indexes0 {
        public static Index JOB_ID_PK = Internal.createIndex("job_id_pk", Job.JOB, new OrderField[] { Job.JOB.ID }, true);
        public static Index JOB_ID_UINDEX = Internal.createIndex("job_id_uindex", Job.JOB, new OrderField[] { Job.JOB.ID }, true);
        public static Index JOB_UUID_UINDEX = Internal.createIndex("job_uuid_uindex", Job.JOB, new OrderField[] { Job.JOB.UUID }, true);
        public static Index TASK_ID_PK = Internal.createIndex("task_id_pk", Task.TASK, new OrderField[] { Task.TASK.ID }, true);
        public static Index TASK_ID_UINDEX = Internal.createIndex("task_id_uindex", Task.TASK, new OrderField[] { Task.TASK.ID }, true);
        public static Index TASK_JOB_INDEX = Internal.createIndex("task_job_index", Task.TASK, new OrderField[] { Task.TASK.JOB }, false);
        public static Index TASK_UUID_UINDEX = Internal.createIndex("task_uuid_uindex", Task.TASK, new OrderField[] { Task.TASK.UUID }, true);
    }
}
