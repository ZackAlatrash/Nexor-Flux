#!/usr/bin/env python3
"""Vendor muscle-region SVG path data from react-native-body-highlighter (MIT).
Pinned commit for reproducibility."""
import json, os, re, urllib.request

SHA = "15df9e2dbc621450001960bed5a30e6a75357faa"
BASE = f"https://raw.githubusercontent.com/HichamELBSI/react-native-body-highlighter/{SHA}/assets"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "muscles")

def extract(ts_text):
    parts = re.split(r'slug:\s*"([a-z-]+)"', ts_text)
    out = []
    for i in range(1, len(parts), 2):
        slug = parts[i]
        for d in re.findall(r'"([Mm][^"]+)"', parts[i + 1]):
            out.append({"slug": slug, "d": d})
    return out

def main():
    os.makedirs(OUT, exist_ok=True)
    for src, dst in [("bodyFront.ts", "body_front.json"), ("bodyBack.ts", "body_back.json")]:
        text = urllib.request.urlopen(f"{BASE}/{src}").read().decode("utf-8")
        data = extract(text)
        assert len(data) > 20, f"too few paths parsed from {src}"
        with open(os.path.join(OUT, dst), "w") as f:
            json.dump(data, f)
        print(f"{dst}: {len(data)} paths")

if __name__ == "__main__":
    main()
