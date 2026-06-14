#!/usr/bin/env python3
"""Build app/src/main/assets/knowledge/corpus.json from distilled markdown notes.

Each file in knowledge-sources/ has frontmatter (source, tags) between leading '---' lines and
one or more '## ' sections. Every non-empty section becomes one corpus chunk. Stdlib only;
re-runnable and deterministic (chunks sorted by id).

Per-section tags: if the first non-empty line of a section is `tags: a, b, c`, those become that
chunk's tags (replacing the file-level tags) and the line is dropped from the chunk text. Sections
without their own tags inherit the file-level tags. This lets each chunk carry precise, distinct
tags so retrieval can rank sibling chunks in the same file.

Usage: python3 tools/ingest_knowledge.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "knowledge-sources")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "knowledge", "corpus.json")


def slugify(name):
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def parse_tag_list(raw):
    """Split a comma list into clean tags, stripping surrounding [] and stray quotes."""
    raw = raw.strip().strip("[]")
    tags = []
    for part in raw.split(","):
        t = part.strip().strip('"').strip("'").strip()
        if t:
            tags.append(t)
    return tags


def parse_frontmatter(text):
    m = re.match(r"^---\n(.*?)\n---\n(.*)$", text, re.DOTALL)
    if not m:
        raise ValueError("missing frontmatter")
    meta_block, body = m.group(1), m.group(2)
    meta = {}
    for line in meta_block.splitlines():
        if ":" not in line:
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    source = meta.get("source", "").strip()
    tags = parse_tag_list(meta.get("tags", ""))
    return source, tags, body


def split_sections(body):
    sections = []
    title = None
    lines = []
    for line in body.splitlines():
        if line.startswith("## "):
            if title is not None:
                sections.append((title, "\n".join(lines).strip()))
            title = line[3:].strip()
            lines = []
        elif title is not None:
            lines.append(line)
    if title is not None:
        sections.append((title, "\n".join(lines).strip()))
    return sections


def extract_section_tags(section_text, default_tags):
    """If the first non-empty line is `tags: a, b, c`, return (those_tags, text_without_that_line).
    Otherwise return (default_tags, section_text unchanged)."""
    lines = section_text.split("\n")
    idx = 0
    while idx < len(lines) and lines[idx].strip() == "":
        idx += 1
    if idx < len(lines) and lines[idx].strip().lower().startswith("tags:"):
        tags = parse_tag_list(lines[idx].strip()[len("tags:"):])
        body = "\n".join(lines[:idx] + lines[idx + 1:]).strip()
        return (tags if tags else default_tags), body
    return default_tags, section_text


def main():
    if not os.path.isdir(SRC_DIR):
        print("no source dir: %s" % SRC_DIR, file=sys.stderr)
        sys.exit(1)
    chunks = []
    for fname in sorted(os.listdir(SRC_DIR)):
        if not fname.endswith(".md"):
            continue
        with open(os.path.join(SRC_DIR, fname), encoding="utf-8") as f:
            text = f.read()
        source, tags, body = parse_frontmatter(text)
        file_slug = slugify(fname[:-3])
        for i, (title, raw_section) in enumerate(split_sections(body), start=1):
            section_tags, section_text = extract_section_tags(raw_section, tags)
            if not section_text:
                continue
            chunks.append({
                "id": "%s-%d" % (file_slug, i),
                "title": title,
                "tags": section_tags,
                "source": source,
                "text": section_text,
            })
    chunks.sort(key=lambda c: c["id"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"chunks": chunks}, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("wrote %d chunks to %s" % (len(chunks), OUT))


if __name__ == "__main__":
    main()
