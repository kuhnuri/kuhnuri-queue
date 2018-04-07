INSERT INTO job
(id, uuid, created, input, output, finished, priority)
VALUES
  (1, 'a', now() - INTERVAL '1 day', 'file://in', 'file://out', NULL, 0),
  (2, 'b', now(), 'file://in', 'file://out', NULL, 0);

INSERT INTO task
(id, uuid, job, transtype, input, output, status, processing, finished, worker, position)
VALUES
  (1, 'a1', 1, 'html5', NULL, NULL, 'queue', NULL, NULL, NULL, 1),
  (2, 'a2', 1, 'upload', NULL, NULL, 'queue', NULL, NULL, NULL, 2),
  (3, 'b1', 2, 'html5', NULL, NULL, 'queue', NULL, NULL, NULL, 1),
  (4, 'b2', 2, 'upload', NULL, NULL, 'queue', NULL, NULL, NULL, 2);
