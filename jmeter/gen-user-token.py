"""生成用户端 JWT（仅本地测试环境使用）。

与 ysd-common JwtUtil 对齐：HS256 + SHA-256(secret) 作为 HMAC key，claim 为 userId。
用法:
    python jmeter/gen-user-token.py 4          # 默认 7 天有效期
    python jmeter/gen-user-token.py 4 7200     # 自定义秒数
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time

secret = os.environ.get("JWT_USER_SECRET", "dev-only-user-secret-change-me")
key = hashlib.sha256(secret.encode("utf-8")).digest()


def b64(raw: bytes) -> bytes:
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


uid = int(sys.argv[1]) if len(sys.argv) > 1 else 4
ttl = int(sys.argv[2]) if len(sys.argv) > 2 else 604800

header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
payload = b64(json.dumps({"userId": uid, "exp": int(time.time()) + ttl}, separators=(",", ":")).encode())
signing_input = header + b"." + payload
signature = b64(hmac.new(key, signing_input, hashlib.sha256).digest())
print((signing_input + b"." + signature).decode())
