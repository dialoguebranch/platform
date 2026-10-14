-- Indexes for the time-range filter on GET /variables/history (issue #352), which restricts a
-- user's history to recorded_time in [from, to). The existing indexes (V7) end in `id` and serve
-- the unfiltered newest-first lookups; these end in `recorded_time` (then `id`), so the range is
-- an index range scan rather than a scan of the user's whole history in a project.

CREATE INDEX ix_variable_history_user_project_name_time ON variable_history (user_id, project_id, name, recorded_time, id);
CREATE INDEX ix_variable_history_user_project_time ON variable_history (user_id, project_id, recorded_time, id);
