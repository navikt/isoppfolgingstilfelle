DO $$
BEGIN
  CREATE ROLE "disykefravar-x4wt@knada-gcp.iam" WITH NOLOGIN;
  EXCEPTION WHEN DUPLICATE_OBJECT THEN
  RAISE NOTICE 'not creating role disykefravar-x4wt -- it already exists';
END
$$;
