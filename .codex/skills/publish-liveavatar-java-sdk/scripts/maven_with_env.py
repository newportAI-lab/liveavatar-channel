"""Run one Maven command with a safely resolved GPG_PASSPHRASE.

The current process environment wins. Otherwise this parses only the
GPG_PASSPHRASE assignment from a protected, Git-ignored repository .env.
It never sources the file or prints the value.
"""

import argparse
import os
import stat
import subprocess
import sys
from pathlib import Path


VARIABLE = "GPG_PASSPHRASE"


def git_succeeds(repo, *args):
    return (
        subprocess.run(
            ["git", *args],
            cwd=repo,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        ).returncode
        == 0
    )


def resolve_passphrase(repo, environ):
    existing = environ.get(VARIABLE)
    if existing:
        return existing

    repo = Path(repo).resolve()
    path = repo / ".env"
    if not path.is_file() or path.is_symlink():
        raise ValueError("Repository .env is missing or unsafe")
    if stat.S_IMODE(path.stat().st_mode) != 0o600:
        raise ValueError("Repository .env permissions must be 0600")
    if not git_succeeds(repo, "check-ignore", "--quiet", ".env"):
        raise ValueError("Repository .env must be Git-ignored")
    if git_succeeds(repo, "ls-files", "--error-unmatch", ".env"):
        raise ValueError("Repository .env must not be tracked or staged")

    prefix = VARIABLE + "="
    values = [line[len(prefix) :] for line in path.read_text(encoding="utf-8").splitlines() if line.startswith(prefix)]
    if len(values) != 1:
        raise ValueError("Repository .env must contain exactly one {} assignment".format(VARIABLE))
    if not values[0]:
        raise ValueError("{} must not be empty".format(VARIABLE))
    return values[0]


def child_environment(repo, environ):
    child = dict(environ)
    child[VARIABLE] = resolve_passphrase(repo, environ)
    return child


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("provide a Maven command after --")
    try:
        return subprocess.run(
            command,
            cwd=Path(args.repo).resolve(),
            env=child_environment(args.repo, os.environ),
            check=False,
        ).returncode
    except (OSError, UnicodeError, ValueError) as error:
        parser.exit(1, "{}: {}\n".format(type(error).__name__, error))


if __name__ == "__main__":
    sys.exit(main())
