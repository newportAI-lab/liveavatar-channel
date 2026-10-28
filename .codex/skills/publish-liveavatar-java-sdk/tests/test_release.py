import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SKILL = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("release", SKILL / "scripts" / "release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class Response:
    def __init__(self, data):
        self.data = data

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return self.data


class ReleaseManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.sdk = self.root / "liveavatar-channel-sdk"
        self.target = self.sdk / "target"
        self.target.mkdir(parents=True)
        self.version = "2.4.0"
        self.pom = self.sdk / "pom.xml"
        self.pom.write_text(
            """<project xmlns=\"http://maven.apache.org/POM/4.0.0\">
<modelVersion>4.0.0</modelVersion><groupId>io.github.newportai-lab</groupId>
<artifactId>liveavatar-channel-sdk</artifactId><version>2.4.0</version></project>\n""",
            encoding="utf-8",
        )
        names = [
            f"liveavatar-channel-sdk-{self.version}.jar",
            f"liveavatar-channel-sdk-{self.version}-sources.jar",
            f"liveavatar-channel-sdk-{self.version}-javadoc.jar",
        ]
        for name in names:
            artifact = self.target / name
            artifact.write_bytes(name.encode())
            artifact.with_name(name + ".asc").write_bytes(b"signature:" + name.encode())
        (self.sdk / "pom.xml.asc").write_bytes(b"pom-signature")
        self.manifest = self.root / "release.json"

    def tearDown(self):
        self.temp.cleanup()

    def test_record_and_check_detect_artifact_changes(self):
        data = release.record(self.root, self.manifest)
        self.assertEqual(data["coordinates"], "io.github.newportai-lab:liveavatar-channel-sdk:2.4.0")
        self.assertEqual(len(data["files"]), 8)
        release.check(self.manifest)

        jar = self.target / f"liveavatar-channel-sdk-{self.version}.jar"
        jar.write_bytes(b"replaced")
        with self.assertRaisesRegex(ValueError, "Artifact changed"):
            release.check(self.manifest)

    def test_verify_compares_every_remote_hash(self):
        data = release.record(self.root, self.manifest)
        approved_hash = release.digest(self.manifest)
        payloads = {
            release.remote_url(data, entry["remote_name"]): Path(entry["path"]).read_bytes()
            for entry in data["files"]
        }
        release.verify(
            self.manifest,
            approved_hash,
            lambda url, timeout=30: Response(payloads[url]),
        )

        first = data["files"][0]
        payloads[release.remote_url(data, first["remote_name"])] = b"different"
        with self.assertRaisesRegex(ValueError, "Remote hash mismatch"):
            release.verify(
                self.manifest,
                approved_hash,
                lambda url, timeout=30: Response(payloads[url]),
            )

    def test_manifest_does_not_store_remote_urls(self):
        data = release.record(self.root, self.manifest)
        self.assertTrue(all("url" not in entry for entry in data["files"]))

    def test_check_rejects_unexpected_remote_artifact_name(self):
        data = release.record(self.root, self.manifest)
        data["files"][0]["remote_name"] = "not-the-sdk.pom"
        self.manifest.write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Unexpected remote artifact set"):
            release.check(self.manifest)

    def test_verify_rejects_changed_manifest_before_network_access(self):
        data = release.record(self.root, self.manifest)
        approved_hash = release.digest(self.manifest)
        data["files"][0]["path"] = str(self.target / "different-file")
        self.manifest.write_text(json.dumps(data), encoding="utf-8")

        calls = []
        with self.assertRaisesRegex(ValueError, "Manifest SHA-256 mismatch"):
            release.verify(
                self.manifest,
                approved_hash,
                lambda url, timeout=30: calls.append(url),
            )
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
