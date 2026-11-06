import importlib.util
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SKILL = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "maven_with_env", SKILL / "scripts" / "maven_with_env.py"
)
maven_with_env = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(maven_with_env)


class MavenWithEnvTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        (self.repo / ".gitignore").write_text("/.env\n", encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def write_env(self, text, mode=0o600):
        path = self.repo / ".env"
        path.write_text(text, encoding="utf-8")
        path.chmod(mode)
        return path

    def test_reads_literal_value_without_executing_it(self):
        value = "literal=$HOME=$(touch should-not-exist)"
        self.write_env("GPG_PASSPHRASE=" + value + "\n")

        actual = maven_with_env.resolve_passphrase(self.repo, {})

        self.assertEqual(actual, value)
        self.assertFalse((self.repo / "should-not-exist").exists())

    def test_prefers_existing_process_environment(self):
        actual = maven_with_env.resolve_passphrase(
            self.repo, {"GPG_PASSPHRASE": "from-process"}
        )
        self.assertEqual(actual, "from-process")

    def test_rejects_group_or_world_accessible_env(self):
        self.write_env("GPG_PASSPHRASE=secret\n", mode=0o644)
        with self.assertRaisesRegex(ValueError, "permissions must be 0600"):
            maven_with_env.resolve_passphrase(self.repo, {})

    def test_rejects_env_that_is_not_git_ignored(self):
        self.write_env("GPG_PASSPHRASE=secret\n")
        (self.repo / ".gitignore").write_text("", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "must be Git-ignored"):
            maven_with_env.resolve_passphrase(self.repo, {})

    def test_rejects_duplicate_or_empty_passphrase(self):
        self.write_env("GPG_PASSPHRASE=one\nGPG_PASSPHRASE=two\n")
        with self.assertRaisesRegex(ValueError, "exactly one"):
            maven_with_env.resolve_passphrase(self.repo, {})

        self.write_env("GPG_PASSPHRASE=\n")
        with self.assertRaisesRegex(ValueError, "must not be empty"):
            maven_with_env.resolve_passphrase(self.repo, {})

    def test_builds_child_environment_without_mutating_parent(self):
        self.write_env("GPG_PASSPHRASE=child-only\n")
        parent = {"PATH": os.environ.get("PATH", "")}

        child = maven_with_env.child_environment(self.repo, parent)

        self.assertEqual(child["GPG_PASSPHRASE"], "child-only")
        self.assertNotIn("GPG_PASSPHRASE", parent)


if __name__ == "__main__":
    unittest.main()
