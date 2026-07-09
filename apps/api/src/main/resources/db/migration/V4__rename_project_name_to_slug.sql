-- Renames the projects.name column to projects.slug, to distinguish it clearly from the
-- unrelated "name" columns on draft_dialogues / published_dialogues (dialogue names) and to
-- avoid confusion with the project's own UUID primary key ("id").
ALTER TABLE projects DROP CONSTRAINT uq_projects_name;
ALTER TABLE projects RENAME COLUMN name TO slug;
ALTER TABLE projects ADD CONSTRAINT uq_projects_slug UNIQUE (slug);
