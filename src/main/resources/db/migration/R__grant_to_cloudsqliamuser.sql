GRANT SELECT ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO "isyfo-analyse";

DO $$
BEGIN
    ANALYZE;
END
$$;
