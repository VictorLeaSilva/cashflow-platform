-- Bancos e roles (03 §3.1, §3.2; D-15): 1 instância, 2 bancos, cada serviço acessa só o seu.
-- *_owner: dono do banco, usado pelo Flyway. *_app: usado pela aplicação; os grants por tabela
-- ficam nas migrações de cada serviço (T-011, T-021).
-- Executado uma vez pelo entrypoint do PostgreSQL (volume vazio) e pelos ITs via Testcontainers.
-- Senhas vêm do ambiente (\getenv, psql 15+); nenhuma senha fica no repositório.

\set ON_ERROR_STOP on

\getenv lancamentos_owner_password LANCAMENTOS_OWNER_PASSWORD
\getenv lancamentos_app_password LANCAMENTOS_APP_PASSWORD
\getenv consolidado_owner_password CONSOLIDADO_OWNER_PASSWORD
\getenv consolidado_app_password CONSOLIDADO_APP_PASSWORD

CREATE ROLE lancamentos_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'lancamentos_owner_password';
CREATE ROLE lancamentos_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'lancamentos_app_password';
CREATE ROLE consolidado_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'consolidado_owner_password';
CREATE ROLE consolidado_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'consolidado_app_password';

CREATE DATABASE lancamentos OWNER lancamentos_owner;
CREATE DATABASE consolidado OWNER consolidado_owner;

-- CONNECT apenas no próprio banco (inclusive o banco padrão fica fechado para PUBLIC).
REVOKE ALL ON DATABASE lancamentos FROM PUBLIC;
REVOKE ALL ON DATABASE consolidado FROM PUBLIC;
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
GRANT CONNECT ON DATABASE lancamentos TO lancamentos_app;
GRANT CONNECT ON DATABASE consolidado TO consolidado_app;

-- No schema public de cada banco: o owner cria objetos; o app só usa (PostgreSQL 15+ já não
-- concede CREATE a PUBLIC, mas o REVOKE explícito documenta a intenção).
\connect lancamentos
ALTER SCHEMA public OWNER TO lancamentos_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO lancamentos_app;

\connect consolidado
ALTER SCHEMA public OWNER TO consolidado_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO consolidado_app;
