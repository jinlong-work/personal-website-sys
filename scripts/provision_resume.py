"""默认只读检查；--apply 创建在线简历表和权限并首次导入静态资料，不覆盖日常编辑。"""
import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / '.local/project-tools'))
import psycopg
from psycopg.types.json import Jsonb
import yaml


def configuration():
    path = ROOT / 'personal-website-admin/src/main/resources/application-dev.yml'
    config = yaml.safe_load(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
    source = config.get('spring', {}).get('datasource', {})
    def setting(environment, field):
        value = os.environ.get(environment) or source.get(field, '')
        match = re.fullmatch(r'\$\{([^:}]+)(?::(.*))?\}', value)
        return os.environ.get(match[1], match[2] or '') if match else value
    database = urlparse(setting('SPRING_DATASOURCE_URL', 'url').removeprefix('jdbc:'))
    if database.scheme != 'postgresql' or not database.hostname:
        raise ValueError('Missing PostgreSQL configuration')
    return dict(host=database.hostname, port=database.port or 5432, dbname=unquote(database.path.lstrip('/')),
                user=setting('SPRING_DATASOURCE_USERNAME', 'username'), password=setting('SPRING_DATASOURCE_PASSWORD', 'password'),
                connect_timeout=10, options='-c statement_timeout=60000 -c lock_timeout=8000')


def provision(db):
    seed = json.loads((ROOT / 'sql/007_resume_seed.json').read_text(encoding='utf-8'))
    with psycopg.connect(**db, autocommit=True) as connection:
        connection.execute((ROOT / 'sql/007_resume_management_postgresql.sql').read_text(encoding='utf-8'))
    with psycopg.connect(**db) as connection:
        connection.execute('INSERT INTO public.website_resume(resume_id, content, update_by) VALUES (1,%s,%s) ON CONFLICT (resume_id) DO NOTHING', (Jsonb(seed), 'migration:007'))


def inspect(db):
    with psycopg.connect(**db) as connection:
        connection.read_only = True
        exists = bool(connection.execute("SELECT to_regclass('public.website_resume')").fetchone()[0])
        result = dict(resumeTable=exists)
        if exists:
            row = connection.execute('SELECT version, jsonb_array_length(content->\'zh\'->\'education\'), jsonb_array_length(content->\'zh\'->\'experience\'), jsonb_array_length(content->\'zh\'->\'skills\') FROM public.website_resume WHERE resume_id=1').fetchone()
            result.update(initialized=bool(row), version=row[0] if row else None,
                          education=row[1] if row else 0, experience=row[2] if row else 0, skillGroups=row[3] if row else 0)
            result['rolePermissions'] = dict(connection.execute("SELECT r.role_key,count(*) FROM public.sys_role_menu rm JOIN public.sys_role r USING(role_id) JOIN public.sys_menu m USING(menu_id) WHERE m.perms LIKE 'website:resume:%%' GROUP BY r.role_key").fetchall())
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        db = configuration()
        if args.apply:
            provision(db)
        print(json.dumps(inspect(db), ensure_ascii=False))
    except Exception as error:
        # Database exception strings can contain connection details; keep output sanitized.
        print(json.dumps(dict(error=type(error).__name__, message='Resume provisioning failed; no credentials printed.')))
        sys.exit(1)
