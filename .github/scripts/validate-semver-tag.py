#!/usr/bin/env python3
"""Validate a release tag against the repository's existing SemVer tags."""

from __future__ import annotations

import re
import subprocess
import sys
from dataclasses import dataclass
from functools import cmp_to_key


TAG_PATTERN = re.compile(
    r"^v(?P<major>0|[1-9][0-9]*)\.(?P<minor>0|[1-9][0-9]*)\."
    r"(?P<patch>0|[1-9][0-9]*)(?:-(?P<prerelease>[0-9A-Za-z-]+"
    r"(?:\.[0-9A-Za-z-]+)*))?(?:\+(?P<build>[0-9A-Za-z-]+"
    r"(?:\.[0-9A-Za-z-]+)*))?$"
)


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int
    prerelease: tuple[str, ...] | None


def parse_tag(tag: str) -> Version | None:
    """Return the SemVer version represented by a conventional ``v`` tag."""
    match = TAG_PATTERN.fullmatch(tag)
    if match is None:
        return None

    prerelease = match.group("prerelease")
    identifiers = tuple(prerelease.split(".")) if prerelease else None
    if identifiers and any(
        identifier.isdigit() and len(identifier) > 1 and identifier.startswith("0")
        for identifier in identifiers
    ):
        return None

    return Version(
        major=int(match.group("major")),
        minor=int(match.group("minor")),
        patch=int(match.group("patch")),
        prerelease=identifiers,
    )


def compare(left: Version, right: Version) -> int:
    """Compare two versions using SemVer 2.0.0 precedence rules."""
    left_core = (left.major, left.minor, left.patch)
    right_core = (right.major, right.minor, right.patch)
    if left_core != right_core:
        return -1 if left_core < right_core else 1

    if left.prerelease is None:
        return 0 if right.prerelease is None else 1
    if right.prerelease is None:
        return -1

    for left_identifier, right_identifier in zip(left.prerelease, right.prerelease):
        if left_identifier == right_identifier:
            continue
        if left_identifier.isdigit() and right_identifier.isdigit():
            return -1 if int(left_identifier) < int(right_identifier) else 1
        if left_identifier.isdigit():
            return -1
        if right_identifier.isdigit():
            return 1
        return -1 if left_identifier < right_identifier else 1

    if len(left.prerelease) == len(right.prerelease):
        return 0
    return -1 if len(left.prerelease) < len(right.prerelease) else 1


def existing_tags() -> list[str]:
    result = subprocess.run(
        ["git", "tag", "--list"],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.splitlines()


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <tag>", file=sys.stderr)
        return 2

    tag = sys.argv[1]
    version = parse_tag(tag)
    if version is None:
        print(
            f"Invalid release tag {tag!r}. Expected v followed by SemVer 2.0.0, "
            "for example v1.2.3.",
            file=sys.stderr,
        )
        return 1

    previous = [
        (existing_tag, parsed)
        for existing_tag in existing_tags()
        if existing_tag != tag and (parsed := parse_tag(existing_tag)) is not None
    ]
    if not previous:
        print(f"{tag} is the first SemVer release tag.")
        return 0

    latest_tag, latest_version = max(
        previous,
        key=cmp_to_key(lambda left, right: compare(left[1], right[1])),
    )
    if compare(version, latest_version) <= 0:
        print(
            f"Release tag {tag} must be greater than the latest SemVer tag "
            f"{latest_tag}.",
            file=sys.stderr,
        )
        return 1

    print(f"Release tag {tag} is greater than the latest SemVer tag {latest_tag}.")
    return 0



if __name__ == "__main__":
    raise SystemExit(main())
