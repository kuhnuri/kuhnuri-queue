delete from job;

INSERT INTO job
(id, uuid, created, input, output, finished, priority)
VALUES
  (1, 'a', now() - INTERVAL '3 day', 'file://in', 'file://out', NULL, 0),
  (2, 'b', now() - INTERVAL '2 day', 'file://in', 'file://out', NULL, 0),
  (3, 'c', now() - INTERVAL '1 day', 'file://in', 'file://out', NULL, 0),
  (4, 'd', now(), 'file://in', 'file://out', NULL, 0);

INSERT INTO task
(id, uuid, job, transtype, input, output, status, processing, finished, worker, position)
VALUES
  (1, 'a1', 1, 'html5', NULL, NULL, 'queue', NULL, NULL, NULL, 1),
  (2, 'a2', 1, 'upload', NULL, NULL, 'queue', NULL, NULL, NULL, 2),
  (3, 'b1', 2, 'html5', NULL, NULL, 'process', NULL, NULL, NULL, 1),
  (4, 'b2', 2, 'upload', NULL, NULL, 'queue', NULL, NULL, NULL, 2),
  (5, 'c1', 3, 'html5', NULL, NULL, 'done', NULL, NULL, NULL, 1),
  (6, 'c2', 3, 'upload', NULL, NULL, 'queue', NULL, NULL, NULL, 2),
  (7, 'd1', 4, 'html5', NULL, NULL, 'done', NULL, NULL, NULL, 1),
  (8, 'd2', 4, 'upload', NULL, NULL, 'error', NULL, NULL, NULL, 2);
