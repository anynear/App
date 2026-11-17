#!/usr/bin/env python3
"""Text for a store ("What's new" in Google Play): one language of release-notes/X.Y.Z.md as plain text, only with what
is new compared to the previous release, and short enough for the store.

    scripts/store-notes.py Deutsch release-notes/0.9.0.md --known previous-release.md --max 500 \
        --more "Alles Neue: https://anynear.app/de/was-ist-neu/" --empty "Kleine Verbesserungen und Fehlerbehebungen."

--known   text of the previous release (GitHub release body, same format; lines before the first "## " are ignored).
          Entries and intro paragraphs with the same text there are left out, so a pre-release lists only what came
          since the build before it. Without --known: the whole language.
--max     characters at most (Google Play: 500). Longer text is cut after a whole entry, followed by --more.
--empty   text when nothing is left (e.g. a build with only internal changes).

Format of the output like `scripts/release-notes.sh --plain`: intro, then each heading on its own line after an empty
line, entries with a bullet. Same rules as the website for "same entry" (text compared after collapsing spaces).
"""
import argparse
import re
import sys


def clean(text):
    return re.sub(r"\s+", " ", text).strip()


def parse(text, language):
    """(intro paragraphs, [(heading, [entries])]) of one language, as the website's parseNotes reads it."""
    intro, sections, paragraph, on = [], [], [], False

    def flush():
        if paragraph:
            joined = clean(" ".join(paragraph))
            if joined and not sections:
                intro.append(joined)
            paragraph.clear()

    for raw in text.replace("\r\n", "\n").split("\n"):
        line = raw.rstrip()
        if line.startswith("## "):
            flush()
            on = line.strip() == f"## {language}"
            continue
        if not on:
            continue
        if line.startswith("### "):
            flush()
            sections.append((clean(line[4:]), []))
        elif line.startswith("- "):
            flush()
            if not sections:
                sections.append(("", []))
            sections[-1][1].append(clean(line[2:]))
        elif not line.strip():
            flush()
        elif not line.startswith("#"):
            paragraph.append(line)
    flush()
    return intro, [(title, items) for title, items in sections if items]


def render(intro, sections):
    blocks = ["\n".join(intro)] if intro else []
    for title, items in sections:
        lines = ([title] if title else []) + [f"• {item}" for item in items]
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("language")
    parser.add_argument("notes")
    parser.add_argument("--known")
    parser.add_argument("--max", type=int, default=0)
    parser.add_argument("--more", default="")
    parser.add_argument("--empty", default="")
    args = parser.parse_args()

    with open(args.notes, encoding="utf-8") as f:
        intro, sections = parse(f.read(), args.language)
    if not sections:
        sys.exit(f"store notes: {args.notes} has no entries for {args.language}")
    known = set()
    if args.known:
        with open(args.known, encoding="utf-8") as f:
            known_intro, known_sections = parse(f.read(), args.language)
        known = set(known_intro) | {item for _, items in known_sections for item in items}
    intro = [p for p in intro if p not in known]
    sections = [(title, [i for i in items if i not in known]) for title, items in sections]
    sections = [(title, items) for title, items in sections if items]

    if not sections:
        print(args.empty)
        return
    text = render(intro, sections)
    if args.max and len(text) > args.max:
        # Fill entry by entry; whatever does not fit any more is behind the link in --more.
        tail = f"\n\n{args.more}" if args.more else "\n…"
        kept_intro, kept = list(intro), []
        if len(render(kept_intro, [(sections[0][0], sections[0][1][:1])]) + tail) > args.max:
            kept_intro = []
        for title, items in sections:
            for item in items:
                trial = kept + [(title, [item])] if not kept or kept[-1][0] != title else kept[:-1] + [(title, kept[-1][1] + [item])]
                if len(render(kept_intro, trial) + tail) > args.max:
                    break
                kept = trial
            else:
                continue
            break
        text = render(kept_intro, kept) + tail if kept else (args.more or text[: args.max - 1] + "…")
    print(text[: args.max] if args.max else text)


if __name__ == "__main__":
    main()
