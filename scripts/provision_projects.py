"""读取本机已忽略的配置，创建项目表、私有图片桶并一次性迁入现有首页内容。

默认仅检查配置与现状；显式 --apply 才执行迁移。不会输出连接地址或密钥。
依赖可安装到 .local/project-tools：psycopg[binary]、minio、PyYAML、Pillow。
"""
import argparse
import hashlib
import io
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
from minio import Minio
from minio.error import S3Error
from PIL import Image, ImageOps
import urllib3
import yaml


def resolve(value):
    if not isinstance(value, str):
        return value
    match = re.fullmatch(r'\$\{([^:}]+)(?::(.*))?\}', value)
    return os.environ.get(match[1], match[2] or '') if match else value


def configuration():
    dev_path = ROOT / 'personal-website-admin/src/main/resources/application-dev.yml'
    dev = yaml.safe_load(dev_path.read_text(encoding='utf-8-sig')) if dev_path.exists() else {}
    local_path = ROOT / '.local/minio.yml'
    local = yaml.safe_load(local_path.read_text(encoding='utf-8-sig')) if local_path.exists() else {}
    datasource = dev.get('spring', {}).get('datasource', {})
    url = os.environ.get('SPRING_DATASOURCE_URL') or resolve(datasource.get('url', ''))
    database = urlparse(url.removeprefix('jdbc:'))
    if database.scheme != 'postgresql' or not database.hostname:
        raise ValueError('Missing PostgreSQL configuration')
    db = dict(host=database.hostname, port=database.port or 5432, dbname=unquote(database.path.lstrip('/')),
              user=os.environ.get('SPRING_DATASOURCE_USERNAME') or resolve(datasource.get('username', '')),
              password=os.environ.get('SPRING_DATASOURCE_PASSWORD') or resolve(datasource.get('password', '')),
              connect_timeout=10, options='-c statement_timeout=60000 -c lock_timeout=8000')
    storage = local.get('app', {}).get('storage', {}).get('minio', {})
    def setting(name, field, default=''):
        return os.environ.get(name) or resolve(local.get(name, storage.get(field, default)))
    endpoint = urlparse(setting('MINIO_ENDPOINT', 'endpoint'))
    if endpoint.scheme not in ('http', 'https') or not endpoint.netloc:
        raise ValueError('Missing MinIO configuration')
    bucket = setting('MINIO_PROJECT_BUCKET', 'project-bucket', 'website-projects')
    client = Minio(endpoint.netloc, access_key=setting('MINIO_ACCESS_KEY', 'access-key'),
                   secret_key=setting('MINIO_SECRET_KEY', 'secret-key'), secure=endpoint.scheme == 'https',
                   http_client=urllib3.PoolManager(timeout=urllib3.Timeout(connect=5, read=20), retries=False))
    return db, client, bucket, local_path


def anonymous_access(policy):
    for statement in policy.get('Statement', []):
        if statement.get('Effect') != 'Allow':
            continue
        principal = statement.get('Principal')
        if principal == '*':
            return True
        if isinstance(principal, dict):
            aws = principal.get('AWS')
            if aws == '*' or isinstance(aws, list) and '*' in aws:
                return True
    return False


def inspect(db, client, bucket):
    with psycopg.connect(**db) as connection:
        connection.read_only = True
        with connection.cursor() as cursor:
            cursor.execute("SELECT to_regclass('public.website_project'), to_regclass('public.website_project_asset')")
            tables = cursor.fetchone()
            cursor.execute('SELECT count(*) FROM public.sys_user')
            users = cursor.fetchone()[0]
            result = dict(projectTable=bool(tables[0]), assetTable=bool(tables[1]), existingUsers=users)
            if all(tables):
                cursor.execute("SELECT count(*), count(*) FILTER(WHERE status='published'), count(*) FILTER(WHERE featured AND status='published') FROM public.website_project WHERE del_flag='0'")
                projects, published, featured = cursor.fetchone()
                cursor.execute('SELECT path FROM public.website_project_asset')
                paths = [row[0] for row in cursor.fetchall()]
                cursor.execute("SELECT count(*) FROM public.website_project p CROSS JOIN LATERAL jsonb_array_elements_text(p.images) image(path) WHERE NOT EXISTS (SELECT 1 FROM public.website_project_asset a WHERE a.path=image.path)")
                missing_assets = cursor.fetchone()[0]
                cursor.execute("SELECT r.role_key, count(*) FROM public.sys_role_menu rm JOIN public.sys_role r USING(role_id) JOIN public.sys_menu m USING(menu_id) WHERE m.perms LIKE 'website:project:%%' GROUP BY r.role_key ORDER BY r.role_key")
                permissions = dict(cursor.fetchall())
                result.update(projects=projects, published=published, featured=featured, imageAssets=len(paths), missingImageRecords=missing_assets, rolePermissions=permissions)
    exists = client.bucket_exists(bucket)
    result.update(projectBucketExists=exists, projectBucket=bucket)
    if exists:
        try:
            policy = json.loads(client.get_bucket_policy(bucket))
            result['anonymousBucketAccess'] = anonymous_access(policy)
        except S3Error as failure:
            if failure.code != 'NoSuchBucketPolicy':
                raise
            result['anonymousBucketAccess'] = False
        if all(tables):
            objects = {obj.object_name for obj in client.list_objects(bucket, prefix='projects/', recursive=True)}
            result['storedObjects'] = len(objects)
            result['missingStoredImages'] = sum('projects/' + path.rsplit('/', 1)[-1] not in objects for path in paths)
    return result


def image_bytes(path):
    with Image.open(path) as source:
        if max(source.size) > 8192 or source.width * source.height > 32_000_000:
            raise ValueError('Seed image exceeds project limits')
        source.seek(0)
        image = ImageOps.exif_transpose(source).convert('RGBA')
        image.thumbnail((2560, 2560), Image.Resampling.LANCZOS)
        background = Image.new('RGB', image.size, 'white')
        background.paste(image, mask=image.getchannel('A'))
        output = io.BytesIO()
        background.save(output, 'JPEG', quality=90, optimize=True)
        return output.getvalue(), image.width, image.height


def ensure_private_bucket(client, bucket):
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)
    try:
        policy = json.loads(client.get_bucket_policy(bucket))
    except S3Error as failure:
        if failure.code != 'NoSuchBucketPolicy':
            raise
        return
    if anonymous_access(policy):
        raise ValueError('Existing project bucket allows anonymous access; refusing to reuse')


def apply(db, client, bucket, local_path, frontend):
    # DDL 自带事务及迁移标识；重复执行不覆盖已有项目内容。
    with psycopg.connect(**db, autocommit=True) as connection:
        connection.execute((ROOT / 'sql/006_project_management_postgresql.sql').read_text(encoding='utf-8'))
    ensure_private_bucket(client, bucket)
    with psycopg.connect(**db) as connection:
        connection.execute('SELECT pg_advisory_xact_lock(536041, 6)')
        details = connection.execute("SELECT details FROM website_backup_20261016_projects.migration_info WHERE migration_id='006_project_management' FOR UPDATE").fetchone()[0]
        if not details.get('seeded'):
            if connection.execute('SELECT count(*) FROM public.website_project').fetchone()[0]:
                raise ValueError('Projects already exist without seed marker; refusing to overwrite')
            seed = json.loads((ROOT / 'sql/006_project_seed.json').read_text(encoding='utf-8'))['projects']
            paths = {}
            for project in seed:
                for original in project['images']:
                    if original in paths:
                        continue
                    source = (frontend / 'public' / original.lstrip('/')).resolve()
                    if not source.is_relative_to((frontend / 'public/img').resolve()):
                        raise ValueError('Invalid seed image path')
                    data, width, height = image_bytes(source)
                    name = hashlib.sha256(data).hexdigest()[:32] + '.jpg'
                    try:
                        client.stat_object(bucket, 'projects/' + name)
                    except S3Error as failure:
                        if failure.code not in ('NoSuchKey', 'NoSuchObject'):
                            raise
                        client.put_object(bucket, 'projects/' + name, io.BytesIO(data), len(data), content_type='image/jpeg')
                    stored = '/files/projects/' + name
                    connection.execute('''INSERT INTO public.website_project_asset(path, original_name, width, height, file_size, create_by)
                        VALUES (%s,%s,%s,%s,%s,'migration:006') ON CONFLICT(path) DO NOTHING''',
                        (stored, source.name, width, height, len(data)))
                    paths[original] = stored
                connection.execute('''INSERT INTO public.website_project(project_id,name,project_type,description,category,headline,summary,
                    technologies,achievements,images,english,featured,status,sort_order,create_by,update_by)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'published',%s,'migration:006','migration:006')''',
                    (project['id'], project['name'], project['type'], project['description'], project['category'], project['headline'],
                     project['summary'], Jsonb(project['technologies']), Jsonb(project['achievements']),
                     Jsonb([paths[image] for image in project['images']]), Jsonb(project['english']), project['featured'], project['sortOrder']))
            connection.execute("SELECT setval(pg_get_serial_sequence('public.website_project','project_id'), (SELECT max(project_id) FROM public.website_project), true)")
            connection.execute("UPDATE website_backup_20261016_projects.migration_info SET details=details || %s WHERE migration_id='006_project_management'",
                               (Jsonb(dict(seeded=True, projects=len(seed), images=len(paths), bucket=bucket)),))
        result = connection.execute('''SELECT (SELECT count(*) FROM public.website_project WHERE del_flag='0'),
            (SELECT count(*) FROM public.website_project_asset),
            (SELECT count(*) FROM public.sys_menu WHERE perms LIKE 'website:project:%%')''').fetchone()
    if local_path.exists() and not os.environ.get('MINIO_PROJECT_BUCKET'):
        content = local_path.read_text(encoding='utf-8-sig')
        if not re.search(r'^MINIO_PROJECT_BUCKET\s*:', content, re.M):
            with local_path.open('a', encoding='utf-8') as output:
                output.write(f'\nMINIO_PROJECT_BUCKET: {bucket}\n')
    return dict(schemaApplied=True, projects=result[0], imageAssets=result[1], permissions=result[2], projectBucket=bucket, privateBucket=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--frontend-root', type=Path, default=ROOT.parent / 'personal-website')
    arguments = parser.parse_args()
    try:
        settings = configuration()
        result = apply(*settings, arguments.frontend_root.resolve()) if arguments.apply else inspect(*settings[:3])
        print(json.dumps(result, ensure_ascii=True))
    except Exception as failure:
        # 连接异常可能包含部署地址；只记录错误类型，不输出凭据或原始连接参数。
        print(json.dumps(dict(success=False, errorType=type(failure).__name__,
                              errorCode=getattr(failure, 'sqlstate', None) or getattr(failure, 'code', None))))
        sys.exit(1)
