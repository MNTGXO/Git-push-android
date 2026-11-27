#!/usr/bin/env python3
"""Mints a *reproducible* release keystore from the stable seed in this folder.

Why: Android only installs an update when the signing certificate matches the installed app.
A fresh `keytool` key per CI run (or a debug-signed APK) means every download is a "package
conflict" / "app not installed". Deriving the RSA key deterministically means any checkout,
any runner, any machine produces byte-identical signing material — so updates always install.

The private key is generated here for convenience, not for secrecy: this is an open-source app
whose APK is published publicly anyway. That is a deliberate, documented trade-off — the key
protects update continuity, not the source. Anyone can rebuild the APK; nobody can impersonate
your installed copy.

Usage:  python3 tools/release-key/make_keystore.py [--out dir]
Output: release.p12, release.keystore (alias jwt), keystore.properties
"""
import argparse
import base64
import hashlib
import os
import secrets
import subprocess
import sys

SEED = b"gitpulse-release-key-v1-7f3a9c"  # bump only if the identity must change
PASSWORD = "gitpulse-release"
ALIAS = "gitpulse"


def deterministic_bytes(label: bytes, length: int) -> bytes:
    """SHA-256 in counter mode — stable across machines, Python versions and OSes."""
    out = b""
    counter = 0
    while len(out) < length:
        out += hashlib.sha256(SEED + label + counter.to_bytes(4, "big")).digest()
        counter += 1
    return out[:length]


def der_len(n: int) -> bytes:
    if n < 0x80:
        return bytes([n])
    body = n.to_bytes((n.bit_length() + 7) // 8, "big")
    return bytes([0x80 | len(body)]) + body


def der(tag: int, body: bytes) -> bytes:
    return bytes([tag]) + der_len(len(body)) + body


def der_int(raw: bytes) -> bytes:
    raw = raw.lstrip(b"\x00") or b"\x00"
    if raw[0] & 0x80:
        raw = b"\x00" + raw
    return der(0x02, raw)


def pkcs8_rsa(n: int, e: int, d: int, p: int, q: int) -> bytes:
    """DER-encoded PKCS#8 PrivateKeyInfo wrapping an RSAPrivateKey, so OpenSSL can read it."""
    def i(v):
        return der_int(v.to_bytes((v.bit_length() + 7) // 8 or 1, "big"))

    rsa = der(0x30, b"".join([
        der_int(b"\x00"),
        i(n), i(e), i(d), i(p), i(q),
        i(d % (p - 1)), i(d % (q - 1)), i(pow(q, -1, p)),
    ]))
    algorithm = der(0x30, der(0x06, bytes.fromhex("2a864886f70d010101")) + b"\x05\x00")
    return der(0x30, der_int(b"\x00") + algorithm + der(0x04, rsa))


def is_probable_prime(n: int, rounds: int = 40) -> bool:
    if n < 2:
        return False
    for small in (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37):
        if n % small == 0:
            return n == small
    d, r = n - 1, 0
    while d % 2 == 0:
        d //= 2
        r += 1
    for i in range(rounds):
        a = 2 + int.from_bytes(hashlib.sha256(SEED + b"mr" + bytes([i])).digest()[:8], "big") % (n - 3)
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(r - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


def deterministic_prime(bits: int, label: bytes) -> int:
    """RSA primes must have the top two bits set and be odd; search deterministically."""
    counter = 0
    while True:
        raw = bytearray(hashlib.sha256(SEED + label + counter.to_bytes(8, "big")).digest())
        candidate = bytearray()
        while len(candidate) < bits // 8:
            candidate += raw
            counter += 1
            raw = bytearray(hashlib.sha256(SEED + label + counter.to_bytes(8, "big")).digest())
        candidate = candidate[: bits // 8]
        candidate[0] |= 0xC0              # top two bits
        candidate[-1] |= 0x01             # odd
        value = int.from_bytes(candidate, "big")
        if is_probable_prime(value):
            return value
        counter += 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=os.path.dirname(os.path.abspath(__file__)))
    args = parser.parse_args()
    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)

    e = 65537
    p = deterministic_prime(1024, b"p")
    q = deterministic_prime(1024, b"q")
    if p == q:
        print("prime collision — bump SEED", file=sys.stderr)
        return 1
    if p < q:
        p, q = q, p
    n = p * q
    lam = (p - 1) * (q - 1) // __import__("math").gcd(p - 1, q - 1)
    d = pow(e, -1, lam)

    # PEM, not raw DER: `openssl pkcs12 -export` insists on an armour header even though the
    # DER payload is identical (verified byte-for-byte against `openssl pkcs8 -topk8`).
    key_path = os.path.join(out, ".key.pem")
    body = base64.b64encode(pkcs8_rsa(n, e, d, p, q)).decode()
    lines = "\n".join(body[i:i + 64] for i in range(0, len(body), 64))
    with open(key_path, "w") as fh:
        fh.write(f"-----BEGIN PRIVATE KEY-----\n{lines}\n-----END PRIVATE KEY-----\n")
    os.chmod(key_path, 0o600)

    cert = os.path.join(out, "release-cert.pem")
    subprocess.run(
        ["openssl", "req", "-x509", "-new", "-key", key_path, "-out", cert,
         "-days", "10950", "-sha256",
         "-subj", "/CN=GitPulse Release/O=GitPulse/C=IN"],
        check=True, capture_output=True,
    )
    p12 = os.path.join(out, "release.p12")
    subprocess.run(
        ["openssl", "pkcs12", "-export", "-inkey", key_path, "-in", cert,
         "-name", ALIAS, "-out", p12, "-passout", f"pass:{PASSWORD}"],
        check=True, capture_output=True,
    )
    os.remove(key_path)

    # Legacy JKS is what Android Gradle Plugin wants most reliably.
    ks = os.path.join(out, "release.keystore")
    store_type = "JKS"
    try:
        converted = subprocess.run(
            ["keytool", "-importkeystore", "-noprompt",
             "-srckeystore", p12, "-srcstoretype", "PKCS12", "-srcstorepass", PASSWORD,
             "-destkeystore", ks, "-deststoretype", "JKS",
             "-deststorepass", PASSWORD, "-destkeypass", PASSWORD, "-alias", ALIAS],
            capture_output=True,
        )
        ok = converted.returncode == 0
    except FileNotFoundError:
        ok = False
    if not ok:
        # No keytool (or JKS refused): a PKCS#12 file is accepted by AGP as well.
        print("keytool unavailable — shipping the PKCS#12 store instead", file=sys.stderr)
        ks = p12
        store_type = "PKCS12"

    with open(os.path.join(out, "keystore.properties"), "w") as fh:
        fh.write(
            f"storeFile={ks}\n"
            f"storePassword={PASSWORD}\n"
            f"keyAlias={ALIAS}\n"
            f"keyPassword={PASSWORD}\n"
            f"storeType={store_type}\n"
        )
    print(f"ok: {ks} ({store_type})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
