"""The ID of the first profile of a type in a copy of the app's database (Room's proxy_entities), or of the newest
with a third argument "last"; nothing while there is none or the copy was taken mid-write. Arguments: the database
file, the type (ProxyEntity.TYPE_*), optionally "last" (ci.yml, e2e)."""
import sqlite3
import sys

try:
    db = sqlite3.connect(sys.argv[1])
    order = "DESC" if sys.argv[3:4] == ["last"] else "ASC"
    row = db.execute("SELECT id FROM proxy_entities WHERE type = ? ORDER BY id %s LIMIT 1" % order, (int(sys.argv[2]),)).fetchone()
except sqlite3.Error:
    row = None
if row:
    print(row[0])
