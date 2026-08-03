#!/usr/bin/env python3
"""Assert an AAB declares the permissions we think it does, and no others.

    python3 scripts/aab-permissions.py [path/to/app-release.aab]

Why this exists: at 4h the note in `docs/play-app-content.md` said 1.1 declared
two permissions. The artifact declared six. WorkManager's manifest had merged in
WAKE_LOCK, ACCESS_NETWORK_STATE and FOREGROUND_SERVICE, none of which appear
anywhere in this app's source — the same "a dependency wrote a permission into
the merged manifest" hazard that file already warns about for the advertising
ID, arriving somewhere nobody was watching.

So the permission set is asserted against a list kept here, and adding a
dependency that merges a new one **fails** rather than passing quietly. When it
does fail, the fix is to decide what the new permission means for the Play
Console, write that into docs/play-app-content.md, and only then add it below.

`strings | grep` cannot do this job: it cannot tell a <uses-permission> from an
android:permission guard on a service, and this artifact carries three of the
latter (BIND_JOB_SERVICE, and DUMP twice) that are not requests at all. So the
protobuf gets walked properly.

Exits non-zero if the artifact's <uses-permission> set differs from EXPECTED.
"""

import sys
import zipfile

# Field numbers from aapt2's Resources.proto, hard-coded for the same reason
# scripts/aab-version.py hard-codes them: a few numbers beat a protoc dependency
# in a script whose whole job is to have no moving parts.
NODE_ELEMENT = 1
ELEM_NAME, ELEM_ATTRIBUTE, ELEM_CHILD = 3, 4, 5
ATTR_NAME, ATTR_VALUE = 2, 3

# Every <uses-permission> the release artifact is allowed to carry. Each is
# accounted for in docs/play-app-content.md — keep the two in step.
EXPECTED = {
    "android.permission.POST_NOTIFICATIONS": "ours — care reminders, ADR-0006",
    "android.permission.RECEIVE_BOOT_COMPLETED": "ours — re-arms the sweep, ADR-0024",
    "android.permission.WAKE_LOCK": "WorkManager",
    "android.permission.ACCESS_NETWORK_STATE": "WorkManager — reads state, not network access",
    "android.permission.FOREGROUND_SERVICE": "WorkManager — never started, see play-app-content.md",
    # AndroidX defines and uses this itself, signature-level. The prefix is the
    # applicationId, which differs between the debug and release builds, so it is
    # matched by suffix rather than spelled out.
    "*.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION": "AndroidX, signature-level",
}

# Permissions that must never appear. Absence is what the Data safety answers
# rest on, so it is asserted rather than assumed.
FORBIDDEN = {
    "android.permission.INTERNET": "Data safety says nothing leaves the device",
    "com.google.android.gms.permission.AD_ID": "Data safety says no advertising ID",
    "android.permission.QUERY_ALL_PACKAGES": "the <queries> element names one package instead",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": "Play restricts it; ADR-0003 deep-links instead",
}


def read_varint(buf, i):
    shift = result = 0
    while True:
        byte = buf[i]
        i += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, i
        shift += 7


def fields(buf):
    """Yield (field_number, payload) for one protobuf message."""
    i = 0
    while i < len(buf):
        key, i = read_varint(buf, i)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, i = read_varint(buf, i)
            yield number, value
        elif wire == 1:
            yield number, buf[i:i + 8]
            i += 8
        elif wire == 2:
            length, i = read_varint(buf, i)
            yield number, buf[i:i + length]
            i += length
        elif wire == 5:
            yield number, buf[i:i + 4]
            i += 4
        else:
            raise ValueError(f"unsupported protobuf wire type {wire}")


def as_element(node_blob):
    return next((p for n, p in fields(node_blob) if n == NODE_ELEMENT), None)


def walk(element):
    """Yield (tag, {attribute: value}) for this element and every descendant."""
    tag, attrs, children = None, {}, []
    for number, payload in fields(element):
        if number == ELEM_NAME and isinstance(payload, bytes):
            tag = payload.decode(errors="replace")
        elif number == ELEM_ATTRIBUTE:
            name = value = None
            for anumber, apayload in fields(payload):
                if anumber == ATTR_NAME and isinstance(apayload, bytes):
                    name = apayload.decode(errors="replace")
                elif anumber == ATTR_VALUE and isinstance(apayload, bytes):
                    value = apayload.decode(errors="replace")
            if name:
                attrs[name] = value
        elif number == ELEM_CHILD:
            child = as_element(payload)
            if child is not None:
                children.append(child)

    yield tag, attrs
    for child in children:
        yield from walk(child)


def matches(permission, allowed):
    return permission == allowed or (
        allowed.startswith("*.") and permission.endswith(allowed[1:])
    )


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/bundle/release/app-release.aab"
    try:
        with zipfile.ZipFile(path) as bundle:
            blob = bundle.read("base/manifest/AndroidManifest.xml")
    except FileNotFoundError:
        sys.exit(f"no such bundle: {path}\nRun ./gradlew bundleRelease first.")
    except KeyError:
        sys.exit(f"{path} has no base/manifest/AndroidManifest.xml — is it an AAB?")

    root = as_element(blob)
    if root is None:
        sys.exit("no root element in base/manifest/AndroidManifest.xml")

    requested, guards = [], []
    for tag, attrs in walk(root):
        name = attrs.get("name")
        if tag in ("uses-permission", "uses-permission-sdk-23") and name:
            requested.append(name)
        elif tag in ("service", "receiver", "provider", "activity") and attrs.get("permission"):
            guards.append((name or "?", attrs["permission"]))

    for permission in sorted(requested):
        note = next((n for a, n in EXPECTED.items() if matches(permission, a)), None)
        print(f"  {'ok ' if note else 'NEW'} {permission}" + (f"  — {note}" if note else ""))

    # Guards are context, not requests: android:permission on a component says who
    # may *call* it. Printed so they are never mistaken for the list above.
    for component, permission in guards:
        print(f"  ·   {permission}  — guard on {component.rsplit('.', 1)[-1]}, not a request")

    unexpected = [p for p in requested if not any(matches(p, a) for a in EXPECTED)]
    absent = [a for a in EXPECTED if not any(matches(p, a) for p in requested)]
    forbidden = {p: why for p, why in FORBIDDEN.items() if p in requested}

    problems = []
    if unexpected:
        problems.append(
            "NEW permissions not accounted for in docs/play-app-content.md:\n"
            + "\n".join(f"  {p}" for p in sorted(unexpected))
            + "\nDecide what each means for the Play Console, write it down, then add it to EXPECTED."
        )
    if absent:
        problems.append(
            "EXPECTED permissions missing from the artifact:\n"
            + "\n".join(f"  {p}" for p in sorted(absent))
        )
    if forbidden:
        problems.append(
            "FORBIDDEN permissions present:\n"
            + "\n".join(f"  {p} — {why}" for p, why in sorted(forbidden.items()))
        )

    if problems:
        print("\n" + "\n\n".join(problems) + "\n\nDo not upload this artifact.", file=sys.stderr)
        return 1

    print(f"\n{len(requested)} permissions, all accounted for; none of the {len(FORBIDDEN)} forbidden ones present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
