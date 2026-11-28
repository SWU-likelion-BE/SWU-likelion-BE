alter table qa_thread add column title varchar(255)
    check (title is null or length(btrim(title)) > 0);
