"""Record, recheck, and remotely verify LiveAvatar Java SDK release artifacts.

This tool never builds, signs, deploys, publishes, or reads credentials.
"""

import argparse
import hashlib
import hmac
import json
import re
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


GROUP_ID = "io.github.newportai-lab"
ARTIFACT_ID = "liveavatar-channel-sdk"
REPOSITORY = "https://repo1.maven.org/maven2"


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def pom_coordinates(path):
    root = ET.parse(path).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{")
    prefix = "{{{}}}".format(namespace) if namespace else ""

    def required(name):
        value = root.findtext(prefix + name)
        if not value or not value.strip():
            raise ValueError("Missing {} in SDK POM".format(name))
        return value.strip()

    return required("groupId"), required("artifactId"), required("version")


def signature_for(path, fallback=None):
    direct = path.with_name(path.name + ".asc")
    if direct.is_file():
        return direct
    if fallback and fallback.is_file():
        return fallback
    raise ValueError("Missing signature for {}".format(path))


def coordinates(data):
    parts = str(data.get("coordinates", "")).split(":")
    if len(parts) != 3 or tuple(parts[:2]) != (GROUP_ID, ARTIFACT_ID):
        raise ValueError("Unexpected coordinates")
    version = parts[2]
    if version.endswith("-SNAPSHOT") or not re.fullmatch(r"[A-Za-z0-9_.-]+", version):
        raise ValueError("Invalid release version: {}".format(version))
    return parts[0], parts[1], version


def expected_remote_names(version):
    base = "{}-{}".format(ARTIFACT_ID, version)
    artifacts = [base + ".pom", base + ".jar", base + "-sources.jar", base + "-javadoc.jar"]
    return {name for artifact in artifacts for name in (artifact, artifact + ".asc")}


def remote_url(data, remote_name):
    if data.get("repository") != REPOSITORY:
        raise ValueError("Unexpected repository")
    group, artifact, version = coordinates(data)
    if remote_name not in expected_remote_names(version):
        raise ValueError("Unexpected remote artifact name: {}".format(remote_name))
    return "/".join((REPOSITORY, group.replace(".", "/"), artifact, version, remote_name))


def record(repo, manifest_path):
    repo = Path(repo).resolve()
    sdk = repo / "liveavatar-channel-sdk"
    pom = sdk / "pom.xml"
    group, artifact, version = pom_coordinates(pom)
    if (group, artifact) != (GROUP_ID, ARTIFACT_ID):
        raise ValueError("Unexpected Maven coordinates: {}:{}".format(group, artifact))
    if version.endswith("-SNAPSHOT") or not re.fullmatch(r"[A-Za-z0-9_.-]+", version):
        raise ValueError("Invalid release version: {}".format(version))

    target = sdk / "target"
    remote_pom = "{}-{}.pom".format(artifact, version)
    artifacts = [(pom, remote_pom)]
    for suffix in (".jar", "-sources.jar", "-javadoc.jar"):
        name = "{}-{}{}".format(artifact, version, suffix)
        artifacts.append((target / name, name))

    files = []
    for local, remote_name in artifacts:
        if not local.is_file() or local.is_symlink():
            raise ValueError("Missing or unsafe artifact: {}".format(local))
        fallback = target / (remote_name + ".asc") if local == pom else None
        signature = signature_for(local, fallback)
        for path, name in ((local, remote_name), (signature, remote_name + ".asc")):
            files.append(
                {
                    "remote_name": name,
                    "path": str(path.resolve()),
                    "size": path.stat().st_size,
                    "sha256": digest(path),
                }
            )

    data = {
        "coordinates": "{}:{}:{}".format(group, artifact, version),
        "repository": REPOSITORY,
        "files": files,
    }
    manifest_path = Path(manifest_path).resolve()
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data


def load_and_check(manifest_path):
    manifest_path = Path(manifest_path).resolve()
    data = json.loads(manifest_path.read_text(encoding="utf-8"))
    if data.get("repository") != REPOSITORY:
        raise ValueError("Unexpected repository")
    _, _, version = coordinates(data)
    files = data.get("files")
    if not isinstance(files, list) or len(files) != 8:
        raise ValueError("Expected POM, three JARs, and four signatures")
    names = {entry.get("remote_name") for entry in files}
    if names != expected_remote_names(version):
        raise ValueError("Unexpected remote artifact set")
    for entry in files:
        if set(entry) != {"remote_name", "path", "size", "sha256"}:
            raise ValueError("Unexpected manifest file fields")
        path = Path(entry["path"])
        if not path.is_file() or path.is_symlink():
            raise ValueError("Missing or unsafe artifact: {}".format(path))
        if path.stat().st_size != entry["size"] or digest(path) != entry["sha256"]:
            raise ValueError("Artifact changed: {}".format(path))
    return data


def check(manifest_path):
    return load_and_check(manifest_path)


def verify(manifest_path, expected_manifest_sha256, opener=urllib.request.urlopen):
    manifest_path = Path(manifest_path).resolve()
    if not re.fullmatch(r"[0-9a-f]{64}", expected_manifest_sha256 or ""):
        raise ValueError("Invalid expected manifest SHA-256")
    if not hmac.compare_digest(digest(manifest_path), expected_manifest_sha256):
        raise ValueError("Manifest SHA-256 mismatch")
    data = load_and_check(manifest_path)
    for entry in data["files"]:
        url = remote_url(data, entry["remote_name"])
        try:
            with opener(url, timeout=30) as response:
                remote_hash = hashlib.sha256(response.read()).hexdigest()
        except Exception as error:
            raise ValueError("Remote artifact unavailable: {} ({})".format(url, error))
        if remote_hash != entry["sha256"]:
            raise ValueError("Remote hash mismatch: {}".format(entry["remote_name"]))
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    record_parser = commands.add_parser("record")
    record_parser.add_argument("--repo", required=True)
    record_parser.add_argument("--manifest", required=True)
    check_parser = commands.add_parser("check")
    check_parser.add_argument("manifest")
    verify_parser = commands.add_parser("verify")
    verify_parser.add_argument("manifest")
    verify_parser.add_argument("--expected-manifest-sha256", required=True)
    args = parser.parse_args()
    try:
        if args.command == "record":
            data = record(args.repo, args.manifest)
        elif args.command == "check":
            data = check(args.manifest)
        else:
            data = verify(args.manifest, args.expected_manifest_sha256)
        print(json.dumps(data, indent=2))
        manifest = Path(args.manifest).resolve()
        print("Manifest: {}".format(manifest))
        print("Manifest SHA-256: {}".format(digest(manifest)))
    except (OSError, KeyError, ValueError, json.JSONDecodeError, ET.ParseError) as error:
        parser.exit(1, "{}: {}\n".format(type(error).__name__, error))


if __name__ == "__main__":
    main()
