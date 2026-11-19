alter table handover_project
    add column status varchar(30) not null default 'DRAFT',
    add column completeness integer not null default 0,
    add column resource_version bigint not null default 0,
    add column created_at timestamptz not null default current_timestamp,
    add column completed_at timestamptz;