#!/usr/bin/env python3
"""Assert a locale's translated strings actually reached the bundle.

    python3 scripts/aab-locale.py [locale] [path/to/app-release.aab]

Why this exists: 1.0.1 was the release that fixed *Polish not reaching a shipped
artifact at all*. `values-pl/strings.xml` was complete, `PolishTranslationTest`
was green, and the build still went up without the translations. Every check in
that chain read the source; nothing read the artifact. So this one reads the
artifact, and it asserts rather than prints — the same lesson as
`scripts/aab-version.py`, applied to the other thing 3a got wrong.

An AAB keeps every locale's strings in `base/resources.pb`; Play splits them into
per-language APKs at install time. So the bytes are there to look for, and their
absence is exactly the failure 1.0.1 shipped.

Exits non-zero if any translated string is missing from the resource table.
"""

import re
import sys
import zipfile
import xml.etree.ElementTree as ET

RES = "app/src/main/res"

# Android's own backslash escapes, which aapt resolves on the way in — the XML
# holds \' but the resource table holds '. Entities like &amp; are already
# handled by the XML parser before we get here.
UNESCAPE = {r"\'": "'", r"\"": '"', r"\\": "\\", r"\n": "\n", r"\t": "\t"}


def resolve(node):
    """The plain value aapt would store for one <string> element.

    itertext() rather than .text because a styled string (<b>, <i>) keeps its
    markup as spans and its *text* as the value, so joining the pieces is what
    the resource table will hold.
    """
    text = "".join(node.itertext())

    # A value wrapped in double quotes keeps its whitespace and loses the quotes;
    # everything else has the whitespace the author used for line-wrapping
    # collapsed away.
    if len(text) >= 2 and text.startswith('"') and text.endswith('"'):
        text = text[1:-1]
    else:
        text = re.sub(r"\s+", " ", text).strip()

    # Only *then* are Android's own escapes turned into characters. The order is
    # load-bearing: \n means a real newline in the resource table, so unescaping
    # it before the collapse above would flatten it right back into a space and
    # the string would look absent from a bundle that in fact carries it.
    for escaped, plain in UNESCAPE.items():
        text = text.replace(escaped, plain)
    return text


def strings_from(path):
    """{name: value} for every <string> and <plurals><item> in one strings.xml."""
    try:
        root = ET.parse(path).getroot()
    except FileNotFoundError:
        sys.exit(f"no such resource file: {path}")

    values = {}
    for node in root:
        name = node.get("name")
        if node.tag == "string" and name:
            values[name] = resolve(node)
        elif node.tag == "plurals" and name:
            for item in node.findall("item"):
                values[f"{name}[{item.get('quantity')}]"] = resolve(item)
    return values


def main():
    locale = sys.argv[1] if len(sys.argv) > 1 else "pl"
    path = sys.argv[2] if len(sys.argv) > 2 else "app/build/outputs/bundle/release/app-release.aab"

    try:
        with zipfile.ZipFile(path) as bundle:
            table = bundle.read("base/resources.pb")
    except FileNotFoundError:
        sys.exit(f"no such bundle: {path}\nRun ./gradlew bundleRelease first.")
    except KeyError:
        sys.exit(f"{path} has no base/resources.pb — is it an AAB?")

    translated = strings_from(f"{RES}/values-{locale}/strings.xml")
    default = strings_from(f"{RES}/values/strings.xml")

    # A string identical to its English counterpart ("OK", a bunny's name) proves
    # nothing about the translation having shipped — it would be present either
    # way. The ones that *differ* are the load-bearing subset, so they are counted
    # separately and the run fails if there are implausibly few.
    missing, distinct, distinct_missing = [], 0, 0
    for name, value in translated.items():
        if not value:
            continue
        differs = default.get(name) != value
        distinct += differs
        if value.encode() not in table:
            missing.append((name, value))
            distinct_missing += differs

    checked = len([v for v in translated.values() if v])
    print(f"locale       {locale}")
    print(f"checked      {checked} strings from values-{locale}/strings.xml")
    print(f"distinct     {distinct} differ from values/strings.xml")

    if missing:
        print(
            f"\nMISSING: {len(missing)} string(s) are not in base/resources.pb "
            f"({distinct_missing} of them translated).\nDo not upload this artifact.",
            file=sys.stderr,
        )
        for name, value in missing[:15]:
            print(f"  {name} = {value!r}", file=sys.stderr)
        if len(missing) > 15:
            print(f"  … and {len(missing) - 15} more", file=sys.stderr)
        return 1

    if distinct < checked // 2:
        print(
            f"\nSUSPICIOUS: only {distinct} of {checked} strings differ from English.\n"
            "That is what a locale falling back to the default looks like.",
            file=sys.stderr,
        )
        return 1

    print(f"\nall {checked} {locale} strings are present in base/resources.pb")
    return 0


if __name__ == "__main__":
    sys.exit(main())
